"""Android App 解析入口：单条抖音作品解析。"""

import json

import requests

from douyin.client import DouyinApiError, DouyinClient
from douyin.parser import ParseError, parse_share_text


def _failure(code: str, message: str, detail: str = "") -> str:
    payload = {"code": code, "message": message}
    if detail:
        payload["debug_detail"] = str(detail)[:500]
    return json.dumps({"error": payload}, ensure_ascii=False)


def resolve(url: str) -> str:
    """返回统一 JSON 信封；Kotlin 映射器将其转换为强类型结果。"""
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
