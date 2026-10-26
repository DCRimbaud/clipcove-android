/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Adapts the WiFi transfer server into observable settings state.
 */
package com.douyin.feature.settings

import com.douyin.data.transfer.WifiTransferServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Settings-facing adapter around the LAN transfer server and its observable state. */
class WifiTransferController(private val server: WifiTransferServer) {

    val running = MutableStateFlow(false)
    val url = MutableStateFlow("")

    /** Toggles the LAN server and returns whether the resulting state is running. */
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