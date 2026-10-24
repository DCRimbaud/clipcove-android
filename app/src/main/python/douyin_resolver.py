"""Android App 解析入口:单视频解析。"""

import json

from douyin.client import DouyinClient
from douyin.parser import ParseError, parse_share_text


def resolve(url: str) -> str:
    """返回 JSON 字符串,失败时返回 {"error": "..."}。"""
    try:
        aweme_id = parse_share_text(url)
        client = DouyinClient()
        detail = client.fetch_detail(aweme_id)
        info = client.extract_video(detail)
        return json.dumps(info, ensure_ascii=False)
    except ParseError as e:
        return json.dumps({"error": f"链接无效: {e}"})
    except Exception as e:
        return json.dumps({"error": f"解析失败: {e}"})
