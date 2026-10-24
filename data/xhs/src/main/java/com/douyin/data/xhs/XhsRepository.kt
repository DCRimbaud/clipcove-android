package com.douyin.data.xhs

/**
 * 小红书解析仓库接口。
 * 实现方案:WebView 浏览器环境渲染页面,从客户端状态提取完整数据(无需签名逆向)。
 * 返回与抖音统一结构的 JSON:
 * 成功:{"id","title","author","cover","play_url","images":[{url,original}],"stats","duration","create_time"}
 * 失败:{"error":"..."}
 */
interface XhsRepository {

    /** 解析小红书笔记链接/ID,返回统一 JSON 字符串 */
    suspend fun resolve(url: String): String
}
