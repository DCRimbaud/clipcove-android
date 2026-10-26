"""Optional live-network smoke checks for platform parsing infrastructure.

SPDX-License-Identifier: GPL-3.0-only
Copyright (c) 2026 DCRimbaud
Author: DCRimbaud
"""

from __future__ import annotations

import html
import json
import os
import re
import sys
from pathlib import Path
import requests

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "app" / "src" / "main" / "python"))

from douyin_resolver import resolve as resolve_douyin  # noqa: E402
from xhs_resolver import fetch_page as fetch_xhs_page  # noqa: E402

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
)


def check_douyin() -> None:
    """Run one optional live Douyin resolver smoke check."""
    sample = os.getenv("DOUYIN_SMOKE_INPUT", "7345492945006595379")
    result = json.loads(resolve_douyin(sample))
    if result.get("error"):
        raise AssertionError(f"Douyin failed: {result['error']}")
    if not result.get("play_url") and not result.get("images"):
        raise AssertionError("Douyin returned neither video nor images")
    print(f"[OK] Douyin {result.get('aweme_id')}: {result.get('title', '')[:40]}")


def check_xiaohongshu() -> None:
    """Fetch a live Xiaohongshu note and verify the SSR payload is present."""
    session = requests.Session()
    session.headers.update({"User-Agent": UA, "Accept-Language": "zh-CN,zh;q=0.9"})
    supplied = os.getenv("XHS_SMOKE_URL")
    if supplied:
        note_url = supplied
    else:
        feed = session.get("https://www.xiaohongshu.com/explore", timeout=20).text
        links = re.findall(
            r"(/explore/[0-9a-f]{24}\?xsec_token=[^\s\"&<]+)",
            html.unescape(feed),
        )
        if not links:
            raise AssertionError("XHS feed did not expose a fresh note token")
        note_url = "https://www.xiaohongshu.com" + links[0] + "&xsec_source=pc_feed"

    envelope = json.loads(fetch_xhs_page(note_url))
    if envelope.get("error"):
        raise AssertionError(f"XHS failed: {envelope['error']}")
    page = envelope.get("html", "")
    has_note_payload = "noteDetailMap" in page or ("noteData" in page and "noteId" in page)
    if "window.__INITIAL_STATE__" not in page or not has_note_payload:
        raise AssertionError("XHS SSR note data is missing")
    note_id = re.search(r"/explore/([0-9a-f]{24})", note_url)
    print(f"[OK] Xiaohongshu SSR {note_id.group(1) if note_id else 'unknown'}")


def main() -> int:
    """Run all optional platform smoke checks and return a process exit code."""
    failed = False
    for name, check in (("douyin", check_douyin), ("xiaohongshu", check_xiaohongshu)):
        try:
            check()
        except Exception as exc:
            failed = True
            print(f"[FAIL] {name}: {exc}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
