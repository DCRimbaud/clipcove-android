package com.douyin.data.download

import kotlinx.coroutines.flow.StateFlow

/**
 * 下载仓库接口:feature 层只依赖此接口,实现可替换(本地/远程)。
 */
interface DownloadRepository {

    /** 任务列表(最新在前),UI 订阅自动刷新 */
    val tasks: StateFlow<List<DownloadTask>>

    /** 初始化(需 Application Context) */
    fun init(context: android.content.Context)

    /** 加入下载队列 */
    fun enqueue(title: String, filename: String, url: String): DownloadTask

    /** 暂停下载:保留 .part 断点文件,可 resume 续传 */
    fun pause(task: DownloadTask)

    /** 继续下载:从断点(Range)恢复 */
    fun resume(task: DownloadTask)

    /** 删除任务并清理文件 */
    fun remove(task: DownloadTask)
}
