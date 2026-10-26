"""Douyin resolver entry point exposed to the Android Chaquopy runtime.

SPDX-License-Identifier: GPL-3.0-only
Copyright (c) 2026 DCRimbaud
Author: DCRimbaud
"""

import json

import requests

from douyin.client import DouyinApiError, DouyinClient
from douyin.parser import ParseError, parse_share_text


def _failure(code: str, message: str, detail: str = "") -> str:
    """Build the structured JSON error envelope consumed by the Kotlin mapper."""

    payload = {"code": code, "message": message}
    if detail:
        payload["debug_detail"] = str(detail)[:500]
    return json.dumps({"error": payload}, ensure_ascii=False)


def resolve(url: str) -> str:
    """Resolve one Douyin share reference into the normalized JSON envelope.

    Args:
        url: Raw share text, short URL, full URL, or numeric aweme ID.

    Returns:
        JSON with normalized media fields on success, or a structured error.

    Raises:
        This function intentionally converts platform exceptions into the JSON
        error envelope so the Chaquopy boundary never leaks raw Python types.
    """
    try:
        aweme_id = parse_share_text(url)
        client = DouyinClient()
        detail = client.fetch_detail(aweme_id)
        info = client.extract_video(detail)
        return json.dumps(info, ensure_ascii=False)
    except ParseError as e:
        return _failure("INVALID_LINK", f"链接无效: {e}", str(e))
    except DouyinApiError as e:
        return _failure(e.code, str(e), e.debug_detail)
    except requests.Timeout as e:
        return _failure("NETWORK", "请求抖音超时，请检查网络后重试", str(e))
    except requests.RequestException as e:
        return _failure("NETWORK", "网络连接失败，请检查网络后重试", str(e))
    except Exception as e:
        return _failure("UNKNOWN", "抖音解析失败，请稍后重试", f"{type(e).__name__}: {e}")