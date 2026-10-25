"""抖音 Web API 客户端:签名请求详情接口,提取无水印直链。"""

import random
import string
import time
from urllib.parse import urlencode

import requests

from .abogus import ABogus
from .parser import UA

DETAIL_URL = "https://www.douyin.com/aweme/v1/web/aweme/detail/"

# 2026-08 起详情接口由 ArgusSecurityPlugin 做前置校验，缺少该请求头会直接返回 403。
# 当前网关只检查请求头存在，不需要登录 Cookie。
ARGUS_HEADERS = {"x-tt-argus": "1"}


class DouyinApiError(RuntimeError):
    """带稳定错误码的平台异常，供 Kotlin 层映射为用户可操作状态。"""

    def __init__(self, code: str, message: str, debug_detail: str = ""):
        super().__init__(message)
        self.code = code
        self.debug_detail = debug_detail[:500]


# 与 abogus 签名对应的浏览器参数集(改动需与 UA 保持一致)
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
    """选择平台提供且可直接下载的图片直链。

    签名的 ``~tplv-...`` 路径不能简单去掉再请求：CDN 会把去掉路径后的
    URL 当成另一个对象并返回 403。``url_list`` 里的 JPEG 版本仍然是
    平台渲染图（不是带作者水印的 ``download_url_list``），优先选择它，
    这样文件扩展名和响应内容也更一致。
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
    """生成 s_v_web_id (verify_fp)。"""
    base36 = ""
    t = int(time.time() * 1000)
    while t > 0:
        t, r = divmod(t, 36)
        base36 = (chr(ord("a") + r - 10) if r >= 10 else str(r)) + base36
    return f"verify_{base36}"


class DouyinClient:
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
        # ttwid 总是注册(cookie jar),用户 Cookie 解析后合并进 jar,避免覆盖
        self._register_ttwid()
        if cookie:
            for pair in cookie.split(";"):
                pair = pair.strip()
                if "=" in pair:
                    k, v = pair.split("=", 1)
                    self.session.cookies.set(k.strip(), v.strip(), domain=".douyin.com")

    def _register_ttwid(self):
        """注册 ttwid cookie(接口必需)。"""
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
        """从详情数据中提取无水印视频直链等核心信息。

        图集(aweme_type=68/69 等)没有视频画面,其 video.play_addr 实际是音频地址,
        此时返回 images 图片列表,play_url 为空。
        """
        author = detail.get("author", {}) or {}
        stats = detail.get("statistics", {}) or {}
        video = detail.get("video", {}) or {}
        images = detail.get("images") or []

        if images:
            image_items = []
            for img in images:
                url = best_image_url(img.get("url_list") or [])
                item = {
                    "url": url,
                    # 保留字段以兼容旧版 JSON 消费方；该值由客户端自行选择，
                    # 不能通过删除 CDN 签名路径来伪造。
                    "original_url": "",
                    "gif_url": "",
                    "width": img.get("width"),
                    "height": img.get("height"),
                }
                # 动态图(live photo / clip_type=5)自带视频流,下载它才是真正的动图
                img_video = img.get("video") or {}
                gif_addr = img_video.get("play_addr") or {}
                if gif_addr.get("url_list"):
                    item["gif_url"] = gif_addr["url_list"][0]
                image_items.append(item)
            return {
                "aweme_id": str(detail.get("aweme_id")),
                "title": detail.get("desc", "") or "(无标题)",
                "author": author.get("nickname", ""),
                "create_time": detail.get("create_time"),
                "stats": stats,
                "duration": video.get("duration"),
                "cover": ((video.get("cover", {}) or {}).get("url_list") or [""])[0],
                "music": ((detail.get("music", {}) or {}).get("play_url", {}) or {}).get("url_list", [""])[0],
                "music_title": ((detail.get("music", {}) or {}).get("title", "")) or "",
                "images": image_items,
                "play_url": "",
                "play_urls": [],
            }

        play_addr = video.get("play_addr", {}) or {}
        candidates = []
        if play_addr.get("url_list"):
            candidates.append({"label": "play_addr", "url": play_addr["url_list"][0]})
        for i, br in enumerate(detail.get("video_bit_rate") or []):
            pa = br.get("play_addr", {}) or {}
            if pa.get("url_list"):
                candidates.append(
                    {"label": f"bitrate_{i}", "url": pa["url_list"][0], "bitrate": br.get("bit_rate")}
                )
        if not candidates:
            raise DouyinApiError("RESPONSE_CHANGED", "未找到可用的视频地址")
        best = max(candidates, key=lambda c: c.get("bitrate") or 0)
        return {
            "aweme_id": str(detail.get("aweme_id")),
            "title": detail.get("desc", "") or "(无标题)",
            "author": author.get("nickname", ""),
            "create_time": detail.get("create_time"),
            "stats": stats,
            "duration": video.get("duration"),
            "cover": ((video.get("cover", {}) or {}).get("url_list") or [""])[0],
            "music": ((detail.get("music", {}) or {}).get("play_url", {}) or {}).get("url_list", [""])[0],
            "music_title": ((detail.get("music", {}) or {}).get("title", "")) or "",
            "images": [],
            "play_url": best["url"],
            "play_urls": [{"label": c["label"], "url": c["url"], "bitrate": c.get("bitrate")} for c in candidates],
        }
