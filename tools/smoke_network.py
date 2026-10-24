"""可选联网冒烟测试：直接验证当前平台的解析链路。

用法（Windows，在仓库根目录）：
    $env:PYTHONPATH = "app/src/main/python"
    python tools/smoke_network.py

该脚本依赖外部平台，可能因网络、风控或作品失效而失败，不进入普通 CI。
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
    sample = os.getenv("DOUYIN_SMOKE_INPUT", "7345492945006595379")
    result = json.loads(resolve_douyin(sample))
    if result.get("error"):
        raise AssertionError(f"Douyin failed: {result['error']}")
    if not result.get("play_url") and not result.get("images"):
        raise AssertionError("Douyin returned neither video nor images")
    print(f"[OK] Douyin {result.get('aweme_id')}: {result.get('title', '')[:40]}")


def check_xiaohongshu() -> None:
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
    if "window.__INITIAL_STATE__" not in page or "noteDetailMap" not in page:
        raise AssertionError("XHS SSR note data is missing")
    note_id = re.search(r"/explore/([0-9a-f]{24})", note_url)
    print(f"[OK] Xiaohongshu SSR {note_id.group(1) if note_id else 'unknown'}")


def main() -> int:
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
