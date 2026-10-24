package com.douyin.feature.settings

import com.douyin.data.transfer.WifiTransferServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** WiFi 传输控制器:包装服务器实例,暴露状态流 */
class WifiTransferController(private val server: WifiTransferServer) {

    val running = MutableStateFlow(false)
    val url = MutableStateFlow("")

    fun toggle(): Boolean {
        return if (server.isRunning) {
            server.stop()
            running.value = false
            url.value = ""
            true
        } else {
            val ok = server.start()
            if (ok) {
                running.value = true
                url.value = server.getAccessUrlWithToken()
            }
            ok
        }
    }
}
