"""Douyin web API client, signed request transport, and media extraction helpers.

SPDX-License-Identifier: GPL-3.0-only
Copyright (c) 2026 DCRimbaud
Author: DCRimbaud
"""

import random
import string
import time
from urllib.parse import urlencode

import requests

from .abogus import ABogus
from .parser import UA

DETAIL_URL = "https://www.douyin.com/aweme/v1/web/aweme/detail/"

# Since August 2026 the detail endpoint is guarded by ArgusSecurityPlugin.
# The current gateway checks for the header's presence and does not require a login cookie.
ARGUS_HEADERS = {"x-tt-argus": "1"}


class DouyinApiError(RuntimeError):
    """Platform error carrying a stable code and bounded diagnostic detail."""

    def __init__(self, code: str, message: str, debug_detail: str = ""):
        super().__init__(message)
        self.code = code
        self.debug_detail = debug_detail[:500]


# Browser parameters used by the abogus signature; keep them aligned with User-Agent.
BASE_PARAMS = {
    "device_platform": "webapp",
    "aid": "6383",
    "channel": "channel_pc_web",
    "pc_client_type": 1,
    "version_code": "290100",
    "version_name": "29.1.0",
    "cookie_enabled": "true",
    "screen_width": 1920,
    "screen_height": 1080,
    "browser_language": "zh-CN",
    "browser_platform": "Win32",
    "browser_name": "Chrome",
    "browser_version": "130.0.0.0",
    "browser_online": "true",
    "engine_name": "Blink",
    "engine_version": "130.0.0.0",
    "os_name": "Windows",
    "os_version": "10",
    "cpu_core_num": 12,
    "device_memory": 8,
    "platform": "PC",
    "downlink": "10",
    "effective_type": "4g",
    "round_trip_time": "0",
}


TTWID_URL = "https://ttwid.bytedance.com/ttwid/union/register/"
TTWID_PAYLOAD = {
    "region": "cn",
    "aid": 1768,
    "needFid": False,
    "service": "www.ixigua.com",
    "migrate_info": {"ticket": "", "source": "node"},
    "cbUrlProtocol": "https",
    "union": True,
}


def best_image_url(url_list: list[str]) -> str:
    """Select a directly downloadable image URL from a platform response.

    Signed ``~tplv-...`` paths must not be stripped: the CDN treats the
    rewritten path as a different object and returns HTTP 403. Prefer the
    platform-rendered JPEG in ``url_list`` (not the watermarked
    ``download_url_list``) so the extension and response content also agree.
    """
    urls = [url for url in url_list if url]
    if not urls:
        return ""
    return next(
        (
            url
            for url in urls
            if ".jpeg" in url.split("?", 1)[0].lower()
            or ".jpg" in url.split("?", 1)[0].lower()
        ),
        urls[0],
    )


def gen_verify_fp() -> str:
    """Generate the browser-style ``s_v_web_id`` verification fingerprint."""
    base36 = ""
    t = int(time.time() * 1000)
    while t > 0:
        t, r = divmod(t, 36)
        base36 = (chr(ord("a") + r - 10) if r >= 10 else str(r)) + base36
    return f"verify_{base36}"


class DouyinClient:
    """Signed HTTP client for Douyin detail requests and media extraction."""

    def __init__(self, cookie: str = "", timeout: int = 15):
        self.cookie = cookie
        self.timeout = timeout
        self.session = requests.Session()
        self.session.headers.update(
            {
                "User-Agent": UA,
                "Referer": "https://www.douyin.com/",
                "Accept": "application/json, text/plain, */*",
                **ARGUS_HEADERS,
            }
        )
        # Register ttwid in the shared cookie jar, then merge any user-provided cookies.
        self._register_ttwid()
        if cookie:
            for pair in cookie.split(";"):
                pair = pair.strip()
                if "=" in pair:
                    k, v = pair.split("=", 1)
                    self.session.cookies.set(k.strip(), v.strip(), domain=".douyin.com")

    def _register_ttwid(self):
        """Register a ttwid cookie required by the detail endpoint."""
        try:
            resp = self.session.post(TTWID_URL, json=TTWID_PAYLOAD, timeout=self.timeout)
            ttwid = resp.cookies.get("ttwid")
            if ttwid:
                self.session.cookies.set("ttwid", ttwid, domain=".douyin.com")
        except requests.RequestException:
            pass

    @staticmethod
    def _rand_token(n: int = 20) -> str:
        return "".join(random.choices(string.ascii_letters + string.digits, k=n))

    def _signed_url(self, aweme_id: str) -> str:
        params = dict(BASE_PARAMS)
        params.update(
            {
                "aweme_id": aweme_id,
                "msToken": self._rand_token(),
                "X-Bogus": "",
                "a_bogus": "",
            }
        )
        sig = ABogus().get_value(params)
        params["a_bogus"] = sig
        return f"{DETAIL_URL}?{urlencode(params)}"

    def fetch_detail(self, aweme_id: str) -> dict:
        """Fetch one aweme detail object, retrying only transient gateway failures.

        Args:
            aweme_id: Numeric Douyin work identifier.

        Returns:
            The unwrapped ``aweme_detail`` object.

        Raises:
            DouyinApiError: The platform rejects the request or returns an
                unexpected response contract.
        """
        url = self._signed_url(aweme_id)
        for attempt in range(3):
            resp = self.session.get(url, timeout=self.timeout)
            body = resp.text or ""
            if resp.status_code != 200:
                if resp.status_code in (401, 403, 412, 429) or "ArgusSecurityPlugin" in body:
                    raise DouyinApiError(
                        "RISK_CONTROL",
                        "抖音触发了访问风控，请稍后重试",
                        f"HTTP {resp.status_code}: {body[:300]}",
                    )
                if resp.status_code in (404, 410):
                    raise DouyinApiError(
                        "CONTENT_UNAVAILABLE",
                        "作品不存在、已删除或当前不可见",
                        f"HTTP {resp.status_code}",
                    )
                if resp.status_code >= 500:
                    raise DouyinApiError(
                        "NETWORK",
                        "抖音服务暂时不可用，请稍后重试",
                        f"HTTP {resp.status_code}",
                    )
                raise DouyinApiError(
                    "RESPONSE_CHANGED",
                    f"抖音接口返回异常（HTTP {resp.status_code}）",
                    body[:300],
                )

            try:
                data = resp.json()
            except ValueError as exc:
                raise DouyinApiError(
                    "RESPONSE_CHANGED",
                    "抖音接口返回了无法识别的内容",
                    body[:300],
                ) from exc

            if data.get("status_code") in (0, None) and data.get("aweme_detail"):
                return data["aweme_detail"]

            if data.get("status_code") in (5, 404, 2054) and attempt < 2:
                time.sleep(1 + attempt)
                url = self._signed_url(aweme_id)
                continue

            filter_detail = data.get("filter_detail") or {}
            filter_message = filter_detail.get("detail_msg") or filter_detail.get("notice") or ""
            if filter_message:
                raise DouyinApiError(
                    "CONTENT_UNAVAILABLE",
                    str(filter_message),
                    str(filter_detail)[:300],
                )

            status_code = data.get("status_code")
            status_message = str(data.get("status_msg") or "").strip()
            if status_code == 2054:
                raise DouyinApiError(
                    "CONTENT_UNAVAILABLE",
                    status_message or "作品当前不可见",
                    f"status_code={status_code}",
                )
            if status_code == 5:
                raise DouyinApiError(
                    "NETWORK",
                    "抖音服务暂时不可用，请稍后重试",
                    f"status_code={status_code} message={status_message}",
                )
            raise DouyinApiError(
                "RESPONSE_CHANGED",
                status_message or "抖音接口返回异常",
                f"status_code={status_code}",
            )
        raise DouyinApiError("NETWORK", "获取抖音详情失败，请稍后重试")

    def extract_video(self, detail: dict) -> dict:
        """Extract normalized, displayable, and downloadable media fields from detail JSON.

        Args:
            detail: Raw ``aweme_detail`` object returned by the platform API.

        Returns:
            A JSON-serializable dictionary containing common metadata, images,
            video candidates, and soundtrack fields.

        Raises:
            DouyinApiError: No usable video candidate exists for a video item.
        """

        def first_url(value: dict | None) -> str:
            """Return the first non-empty URL from common CDN URL containers."""
            if not isinstance(value, dict):
                return ""
            for key in ("url_list", "urlList", "backup_urls", "backupUrls"):
                urls = value.get(key)
                if isinstance(urls, list):
                    for url in urls:
                        if url:
                            return str(url)
            return ""

        def first_text(*values) -> str:
            """Return the first non-blank value converted to trimmed text."""
            for value in values:
                if value is not None and str(value).strip():
                    return str(value).strip()
            return ""

        author = detail.get("author", {}) or {}
        stats = detail.get("statistics", {}) or {}
        music = detail.get("music", {}) or {}
        video = detail.get("video", {}) or {}
        position = detail.get("position", {}) or {}
        poi = detail.get("poi_info", {}) or {}
        images = detail.get("images") or detail.get("image_list") or []
        description = first_text(detail.get("desc"), detail.get("caption"), "")
        author_avatar = first_url(author.get("avatar_thumb")) or first_url(author.get("avatar_larger"))
        music_play = first_url(music.get("play_url"))
        music_cover = first_url(music.get("cover_large")) or first_url(music.get("cover_medium"))
        hashtags = []
        for item in detail.get("text_extra") or []:
            if isinstance(item, dict):
                name = first_text(item.get("hashtag_name"), item.get("hashtagName"), "")
                if name and name not in hashtags:
                    hashtags.append(name)

        common = {
            "aweme_id": str(detail.get("aweme_id")),
            "title": description or first_text(detail.get("item_title"), "(无标题)"),
            "description": description,
            "author": first_text(author.get("nickname"), author.get("nickName"), ""),
            "author_id": first_text(author.get("uid"), author.get("sec_uid"), ""),
            "author_handle": first_text(author.get("unique_id"), author.get("short_id"), ""),
            "author_avatar": author_avatar,
            "hashtags": hashtags,
            "location": first_text(poi.get("name"), poi.get("poi_name"), position.get("name"), ""),
            "share_url": first_text(detail.get("share_url"), ""),
            "create_time": detail.get("create_time"),
            "stats": stats,
            "duration": video.get("duration") or detail.get("duration") or 0,
            "cover": first_url(video.get("cover")),
            "music": music_play,
            "music_title": first_text(music.get("title"), ""),
            "music_author": first_text(music.get("author"), ""),
            "music_duration": music.get("duration") or 0,
            "music_cover": music_cover,
            "images": [],
            "play_url": "",
            "play_urls": [],
        }

        if images:
            image_items = []
            for img in images:
                if not isinstance(img, dict):
                    continue
                url = best_image_url(img.get("url_list") or [])
                item = {
                    "url": url,
                    # Keep this empty: a stripped CDN path is guaranteed to return HTTP 403.
                    "original_url": "",
                    "gif_url": "",
                    "width": img.get("width") or 0,
                    "height": img.get("height") or 0,
                }
                img_video = img.get("video") or {}
                gif_addr = img_video.get("play_addr") or {}
                if gif_addr.get("url_list"):
                    item["gif_url"] = gif_addr["url_list"][0]
                image_items.append(item)
            common["images"] = image_items
            return common

        play_addr = video.get("play_addr", {}) or {}
        candidates = []
        seen_urls = set()

        def add_candidate(label: str, addr: dict, bitrate: int = 0) -> None:
            """Append a unique video candidate with quality and size metadata."""
            url = first_url(addr)
            if not url or url in seen_urls:
                return
            seen_urls.add(url)
            candidates.append(
                {
                    "label": label,
                    "url": url,
                    "bitrate": bitrate or 0,
                    "width": addr.get("width") or video.get("width") or 0,
                    "height": addr.get("height") or video.get("height") or 0,
                    "size": addr.get("data_size") or addr.get("file_size") or 0,
                }
            )

        add_candidate("播放线路", play_addr)
        for i, br in enumerate(detail.get("video_bit_rate") or []):
            if not isinstance(br, dict):
                continue
            pa = br.get("play_addr", {}) or {}
            add_candidate(
                first_text(br.get("gear_name"), br.get("quality_name"), f"线路 {i + 1}"),
                pa,
                br.get("bit_rate") or 0,
            )
        if not candidates:
            raise DouyinApiError("RESPONSE_CHANGED", "未找到可用的视频地址")

        best = max(candidates, key=lambda item: item.get("bitrate") or 0)
        common["play_url"] = best["url"]
        common["play_urls"] = candidates
        return common