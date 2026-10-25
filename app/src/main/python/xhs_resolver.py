"""小红书页面抓取入口：网络请求在 Python 栈完成，SSR 解析仍由 Kotlin 负责。"""

from __future__ import annotations

import json
from urllib.parse import urlparse

import requests

UA = (
    "Mozilla/5.0 (Linux; Android 14; Pixel 6) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
)
HOME_URL = "https://www.xiaohongshu.com/explore"
ALLOWED_SUFFIXES = ("xiaohongshu.com", "xhslink.com", "xhslink.cn")


def _failure(code: str, message: str, detail: str = "") -> str:
    payload = {"code": code, "message": message}
    if detail:
        payload["debug_detail"] = str(detail)[:500]
    return json.dumps({"error": payload}, ensure_ascii=False)


def _allowed(url: str) -> bool:
    host = (urlparse(url).hostname or "").lower()
    return any(host == suffix or host.endswith("." + suffix) for suffix in ALLOWED_SUFFIXES)


def _has_note_payload(page: str) -> bool:
    """桌面 SSR 和移动端 SSR 使用不同的数据根节点。"""
    return "noteDetailMap" in page or ("noteData" in page and "noteId" in page)


def fetch_page(url: str) -> str:
    """返回 {"html","final_url"} 或结构化错误。"""
    try:
        session = requests.Session()
        session.headers.update(
            {
                "User-Agent": UA,
                "Accept-Language": "zh-CN,zh;q=0.9",
                "Accept": "text/html,application/xhtml+xml,*/*",
                "Referer": "https://www.xiaohongshu.com/",
                "Cache-Control": "no-cache",
            }
        )
        response = session.get(url, timeout=20, allow_redirects=True)
        if not _allowed(response.url):
            return _failure("INVALID_LINK", "小红书短链跳转到了非官方域名", response.url[:200])

        # 部分网络环境首次请求只返回通用页，建立首页会话后再取一次。
        if response.status_code == 200 and not _has_note_payload(response.text):
            session.get(HOME_URL, timeout=20, allow_redirects=True)
            response = session.get(url, timeout=20, allow_redirects=True)

        if response.status_code in (401, 403, 412, 429):
            return _failure("RISK_CONTROL", "小红书访问被限流，请稍后重试", f"HTTP {response.status_code}")
        if response.status_code in (404, 410):
            return _failure("CONTENT_UNAVAILABLE", "笔记不存在、已删除或当前不可见", f"HTTP {response.status_code}")
        if response.status_code >= 500:
            return _failure("NETWORK", "小红书服务暂时不可用，请稍后重试", f"HTTP {response.status_code}")
        if response.status_code != 200:
            return _failure("RESPONSE_CHANGED", f"小红书接口返回异常（HTTP {response.status_code}）", response.text[:300])
        if not response.text.strip():
            return _failure("RESPONSE_CHANGED", "小红书返回了空白页面", "HTTP 200")

        return json.dumps(
            {"html": response.text, "final_url": response.url},
            ensure_ascii=False,
        )
    except requests.Timeout as exc:
        return _failure("NETWORK", "请求小红书超时，请检查网络后重试", str(exc))
    except requests.RequestException as exc:
        return _failure("NETWORK", "网络连接失败，请检查网络后重试", str(exc))
    except Exception as exc:
        return _failure("UNKNOWN", "小红书页面抓取失败，请稍后重试", f"{type(exc).__name__}: {exc}")
