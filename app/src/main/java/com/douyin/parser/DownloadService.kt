/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Publishes active download progress through a foreground notification and stops when no work remains.
 */
package com.douyin.parser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.douyin.core.uiresources.R as SharedR
import com.douyin.data.download.DownloadRepository
import com.douyin.data.download.DownloadStatus
import com.douyin.data.settings.withAppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Download foreground service: keeps active work alive and publishes notification progress.
 * Stops and releases foreground state when every task is complete, failed, or paused.
 */
class DownloadService : Service() {

    private val downloadRepo: DownloadRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectJob: Job? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLanguage())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(getString(SharedR.string.notification_preparing), 0, false),
        )
        collectJob ?: run {
            collectJob = scope.launch {
                downloadRepo.tasks.collectLatest { tasks ->
                    val active = tasks.filter {
                        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
                    }.maxByOrNull { it.id }
                    if (active != null) {
                        val text = if (active.status == DownloadStatus.DOWNLOADING) {
                            "${active.title} ${active.progress}% · ${formatSpeed(active.speedBps)}"
                        } else {
                            getString(SharedR.string.notification_waiting, active.title)
                        }
                        updateNotification(text, active.progress, false)
                    } else {
                        // No active work remains: leave the foreground state.
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(SharedR.string.notification_channel_downloads),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
    }

    private fun buildNotification(text: String, progress: Int, indeterminate: Boolean): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(SharedR.string.notification_title_downloads))
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .build()
    }

    private fun updateNotification(text: String, progress: Int, indeterminate: Boolean) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text, progress, indeterminate))
    }

    override fun onDestroy() {
        collectJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun formatSpeed(bps: Long): String {
        if (bps <= 0) return "--"
        return if (bps >= 1024 * 1024) {
            "%.1f MB/s".format(bps / 1024.0 / 1024.0)
        } else if (bps >= 1024) {
            "%.0f KB/s".format(bps / 1024.0)
        } else {
            "$bps B/s"
        }
    }

    private companion object {
        const val CHANNEL_ID = "download"
        const val NOTIFICATION_ID = 1001
    }
}