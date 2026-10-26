/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Applies deterministic query and status filters to download tasks.
 */
package com.douyin.feature.downloads

import com.douyin.data.download.DownloadStatus
import com.douyin.data.download.DownloadTask

/** User-selectable download status filter. */
internal enum class DownloadFilter {
    ALL,
    ACTIVE,
    DONE,
    FAILED,
}

/** Pure query/status filtering for observable download tasks. */
internal object DownloadTaskFilter {
    /** Applies the status filter and a case-insensitive title/filename search. */
    fun apply(tasks: List<DownloadTask>, query: String, filter: DownloadFilter): List<DownloadTask> {
        val cleanQuery = query.trim()
        return tasks.filter { task ->
            task.matches(filter) && (
                cleanQuery.isEmpty() ||
                    task.title.contains(cleanQuery, ignoreCase = true) ||
                    task.filename.contains(cleanQuery, ignoreCase = true)
                )
        }
    }

    private fun DownloadTask.matches(filter: DownloadFilter): Boolean = when (filter) {
        DownloadFilter.ALL -> true
        DownloadFilter.ACTIVE -> status == DownloadStatus.QUEUED ||
            status == DownloadStatus.DOWNLOADING || status == DownloadStatus.PAUSED
        DownloadFilter.DONE -> status == DownloadStatus.DONE
        DownloadFilter.FAILED -> status == DownloadStatus.FAILED
    }
}