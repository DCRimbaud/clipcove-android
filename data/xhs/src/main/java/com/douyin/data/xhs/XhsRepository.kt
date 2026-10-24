package com.douyin.data.xhs

import com.douyin.core.model.ParseResult

/** 小红书解析端口。当前实现为官方笔记页 SSR，第三方解析接口不再参与主链路。 */
interface XhsRepository {
    suspend fun resolve(url: String): ParseResult
}
