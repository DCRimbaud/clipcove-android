package com.douyin.feature.settings

import androidx.lifecycle.ViewModel
import com.douyin.data.download.DownloadRepository
import com.douyin.data.favorites.FavoritesRepository
import com.douyin.data.history.HistoryRepository
import com.douyin.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 设置页 ViewModel:配置读写 + 清空操作 + WiFi 传输 + 导出备份 + 版本信息 */
class SettingsViewModel(
    private val settingsRepo: SettingsRepository,
    private val historyRepo: HistoryRepository,
    private val downloadRepo: DownloadRepository,
    private val favoritesRepo: FavoritesRepository,
    val wifiTransfer: WifiTransferController,
    val versionName: String,
    val versionCode: Int
) : ViewModel() {

    val autoSave: StateFlow<Boolean> = settingsRepo.autoSaveToDownloads

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    val wifiRunning: StateFlow<Boolean> = wifiTransfer.running
    val wifiUrl: StateFlow<String> = wifiTransfer.url

    fun setAutoSave(value: Boolean) {
        settingsRepo.setAutoSave(value)
        _message.value = if (value) "下载完成后将自动保存到「下载」目录" else "已关闭自动保存"
    }

    fun toggleWifi() {
        val ok = wifiTransfer.toggle()
        _message.value = when {
            ok && wifiTransfer.running.value -> "WiFi 传输已开启"
            ok -> "WiFi 传输已关闭"
            else -> "启动失败,请检查网络"
        }
    }

    fun clearHistory() {
        historyRepo.clear()
        _message.value = "历史记录已清空"
    }

    fun clearDownloads() {
        downloadRepo.tasks.value.forEach { downloadRepo.remove(it) }
        _message.value = "下载列表已清空"
    }

    /** 导出备份(历史 + 收藏),返回 JSON 字符串 */
    fun exportData(): String {
        val arr = org.json.JSONArray()
        historyRepo.load().forEach {
            arr.put(
                org.json.JSONObject()
                    .put("type", "history")
                    .put("awemeId", it.awemeId)
                    .put("title", it.title)
                    .put("cover", it.cover)
                    .put("time", it.time)
            )
        }
        favoritesRepo.load().forEach {
            arr.put(
                org.json.JSONObject()
                    .put("type", "favorite")
                    .put("awemeId", it.awemeId)
                    .put("title", it.title)
                    .put("cover", it.cover)
                    .put("time", it.time)
            )
        }
        return arr.toString(2)
    }

    fun consumeMessage() {
        _message.value = null
    }
}
