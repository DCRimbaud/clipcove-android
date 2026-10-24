package com.douyin.data.settings

import kotlinx.coroutines.flow.StateFlow

/**
 * 设置仓库接口:全局配置项,feature 层只依赖接口。
 */
interface SettingsRepository {

    /** 下载完成自动保存到公共「下载」目录 */
    val autoSaveToDownloads: StateFlow<Boolean>

    fun setAutoSave(value: Boolean)
}
