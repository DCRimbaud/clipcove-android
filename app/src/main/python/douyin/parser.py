"""Share-text and short-link parsing for Douyin references.

SPDX-License-Identifier: GPL-3.0-only
Copyright (c) 2026 DCRimbaud
Author: DCRimbaud
"""

import re

import requests

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
)

# Matches v.douyin.com short links embedded in share text.
SHORT_URL_RE = re.compile(r"https?://v\.douyin\.com/[A-Za-z0-9_\-/]+")
# Matches full Douyin video or note URLs.
FULL_URL_RE = re.compile(r"https?://www\.douyin\.com/(?:video|note)/(\d+)")
# Fallback for a bare aweme ID in user input.
RAW_ID_RE = re.compile(r"\b(\d{15,20})\b")


class ParseError(Exception):
    """Raised when share text does not contain a supported Douyin reference."""


def extract_short_url(text: str) -> str:
    """Extract the first supported short or full URL from share text."""
    m = SHORT_URL_RE.search(text)
    if m:
        return m.group(0)
    m = FULL_URL_RE.search(text)
    if m:
        return m.group(0)
    raise ParseError("未在输入中找到抖音链接")

def resolve_short_url(short_url: str, timeout: int = 15) -> str:
    """Resolve a short link and return its final redirect URL.

    Args:
        short_url: Official Douyin short URL.
        timeout: Per-request timeout in seconds.

    Returns:
        The final URL reported by the redirect chain.
    """
    resp = requests.get(short_url, headers={"User-Agent": UA}, allow_redirects=True, timeout=timeout)
    return resp.url


def aweme_id_from_url(url: str) -> str:
    """Extract a numeric aweme ID from a full or redirected URL."""
    m = FULL_URL_RE.search(url)
    if m:
        return m.group(1)
    m = RAW_ID_RE.search(url)
    if m:
        return m.group(1)
    raise ParseError(f"无法从链接提取视频 ID: {url[:100]}")


def parse_share_text(text: str, timeout: int = 15) -> str:
    """Normalize share text into a numeric Douyin aweme ID.

    Args:
        text: Raw share text, URL, or numeric ID.
        timeout: Redirect timeout used for short links.

    Returns:
        A validated numeric aweme ID.

    Raises:
        ParseError: No supported reference can be extracted.
        requests.RequestException: A short-link request fails.
    """
    text = text.strip()
    # A bare numeric ID is already the final identifier.
    m = RAW_ID_RE.fullmatch(text)
    if m:
        return m.group(1)
    short_url = extract_short_url(text)
    if SHORT_URL_RE.search(short_url) and FULL_URL_RE.search(short_url) is None:
        final_url = resolve_short_url(short_url, timeout=timeout)
        return aweme_id_from_url(final_url)
    return aweme_id_from_url(short_url)