package com.douyin.data.douyin

/**
 * 抖音解析仓库接口:feature 层只依赖此接口。
 * 当前实现基于 Chaquopy 内嵌 Python;未来可替换为 HTTP 服务实现而不影响上层。
 */
interface DouyinRepository {

    /**
     * 解析单条视频/图集,返回 JSON 字符串:
     * 成功:{"aweme_id","title","author","cover","play_url","play_urls","images","music","stats","duration"}
     * 失败:{"error":"..."}
     */
    suspend fun resolve(url: String): String
}
