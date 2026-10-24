"""分享文本 / 链接解析:提取抖音短链并还原为 aweme_id。"""

import re

import requests

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
)

# 匹配 v.douyin.com/xxx 短链(分享口令、网页链接里都可能出现)
SHORT_URL_RE = re.compile(r"https?://v\.douyin\.com/[A-Za-z0-9_\-/]+")
# 直接是完整详情页链接
FULL_URL_RE = re.compile(r"https?://www\.douyin\.com/(?:video|note)/(\d+)")
# 兜底:文本中裸的 19 位数字 id
RAW_ID_RE = re.compile(r"\b(\d{15,20})\b")


class ParseError(Exception):
    pass


def extract_short_url(text: str) -> str:
    """从分享口令/文本中提取短链。"""
    m = SHORT_URL_RE.search(text)
    if m:
        return m.group(0)
    m = FULL_URL_RE.search(text)
    if m:
        return m.group(0)
    raise ParseError("未在输入中找到抖音链接")

def resolve_short_url(short_url: str, timeout: int = 15) -> str:
    """请求短链,跟随重定向,返回最终跳转 URL。"""
    resp = requests.get(short_url, headers={"User-Agent": UA}, allow_redirects=True, timeout=timeout)
    return resp.url


def aweme_id_from_url(url: str) -> str:
    """从(重定向后的)URL 中提取 aweme_id。"""
    m = FULL_URL_RE.search(url)
    if m:
        return m.group(1)
    m = RAW_ID_RE.search(url)
    if m:
        return m.group(1)
    raise ParseError(f"无法从链接提取视频 ID: {url[:100]}")


def parse_share_text(text: str, timeout: int = 15) -> str:
    """解析分享文本/链接,返回 aweme_id。"""
    text = text.strip()
    # 纯数字直接当 aweme_id
    m = RAW_ID_RE.fullmatch(text)
    if m:
        return m.group(1)
    short_url = extract_short_url(text)
    if SHORT_URL_RE.search(short_url) and FULL_URL_RE.search(short_url) is None:
        final_url = resolve_short_url(short_url, timeout=timeout)
        return aweme_id_from_url(final_url)
    return aweme_id_from_url(short_url)
