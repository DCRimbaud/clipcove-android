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


def original_image_url(url: str) -> str:
    """去掉 CDN 压缩参数(~tplv-xxx),取原图地址。"""
    if not url:
        return ""
    path, _, query = url.partition("?")
    if "~tplv-" in path:
        path = path.split("~tplv-")[0]
    return path + ("?" + query if query else "")


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
            data = resp.json()
            if data.get("status_code") in (0, None) and data.get("aweme_detail"):
                return data["aweme_detail"]
            if data.get("status_code") in (5, 404, 2054) and attempt < 2:
                time.sleep(1 + attempt)
                url = self._signed_url(aweme_id)
                continue
            raise RuntimeError(f"接口返回异常: status_code={data.get('status_code')} message={data.get('status_msg')}")
        raise RuntimeError("获取详情失败")

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
                item = {
                    "url": (img.get("url_list") or [""])[0],
                    "original_url": original_image_url((img.get("url_list") or [""])[0]),
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
            raise RuntimeError("未找到可用的视频地址")
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
