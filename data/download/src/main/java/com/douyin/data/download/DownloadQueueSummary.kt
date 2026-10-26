/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Derives stable aggregate counts and progress from download-task snapshots.
 */
package com.douyin.data.download

/**
 * Aggregate download state used by queue controls and foreground notifications.
 *
 * @property total Number of persisted tasks.
 * @property active Number of queued or downloading tasks.
 * @property paused Number of resumable paused tasks.
 * @property completed Number of completed tasks.
 * @property failed Number of tasks awaiting an explicit retry.
 * @property activeProgress Weighted percentage across active tasks.
 */
data class DownloadQueueSummary(
    val total: Int,
    val active: Int,
    val paused: Int,
    val completed: Int,
    val failed: Int,
    val activeProgress: Int,
) {
    /** True when at least one task can be paused or cancelled. */
    val hasActive: Boolean get() = active > 0
    /** True when at least one paused task can resume. */
    val hasPaused: Boolean get() = paused > 0
    /** True when at least one failed task can retry. */
    val hasFailed: Boolean get() = failed > 0
}

/**
 * Calculates queue metrics without mutating task snapshots.
 *
 * Progress is weighted by declared byte totals when available and otherwise
 * falls back to the average task percentage so mixed queues remain meaningful.
 */
fun List<DownloadTask>.toDownloadQueueSummary(): DownloadQueueSummary {
    val activeTasks = filter {
        it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
    }
    val knownTotals = activeTasks.filter { it.totalBytes > 0 }
    val progress = if (knownTotals.isNotEmpty()) {
        val totalBytes = knownTotals.sumOf { it.totalBytes }
        val downloadedBytes = knownTotals.sumOf { task ->
            task.downloadedBytes.coerceIn(0L, task.totalBytes)
        }
        if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
    } else if (activeTasks.isNotEmpty()) {
        activeTasks.sumOf { it.progress.coerceIn(0, 100) } / activeTasks.size
    } else {
        0
    }

    return DownloadQueueSummary(
        total = size,
        active = activeTasks.size,
        paused = count { it.status == DownloadStatus.PAUSED },
        completed = count { it.status == DownloadStatus.DONE },
        failed = count { it.status == DownloadStatus.FAILED },
        activeProgress = progress.coerceIn(0, 100),
    )
}