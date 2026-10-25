package com.douyin.feature.downloads

import com.douyin.data.download.DownloadStatus
import com.douyin.data.download.DownloadTask

internal enum class DownloadFilter {
    ALL,
    ACTIVE,
    DONE,
    FAILED,
}

internal object DownloadTaskFilter {
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