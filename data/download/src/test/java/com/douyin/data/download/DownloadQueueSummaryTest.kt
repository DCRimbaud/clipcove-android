/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Protects aggregate download queue counts and progress invariants.
 */
package com.douyin.data.download

import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies queue summaries consumed by bulk controls and foreground notifications. */
class DownloadQueueSummaryTest {

    /** Counts each lifecycle state independently while excluding terminal work from active totals. */
    @Test
    fun `summary partitions lifecycle states`() {
        val summary = listOf(
            task(1, DownloadStatus.QUEUED, progress = 0),
            task(2, DownloadStatus.DOWNLOADING, progress = 50),
            task(3, DownloadStatus.PAUSED, progress = 25),
            task(4, DownloadStatus.DONE, progress = 100),
            task(5, DownloadStatus.FAILED, progress = 10),
        ).toDownloadQueueSummary()

        assertEquals(5, summary.total)
        assertEquals(2, summary.active)
        assertEquals(1, summary.paused)
        assertEquals(1, summary.completed)
        assertEquals(1, summary.failed)
    }

    /** Weighs known byte totals so a large transfer dominates aggregate notification progress. */
    @Test
    fun `active progress is weighted by declared byte totals`() {
        val summary = listOf(
            task(1, DownloadStatus.DOWNLOADING, totalBytes = 100, downloadedBytes = 40),
            task(2, DownloadStatus.QUEUED, totalBytes = 300, downloadedBytes = 120),
        ).toDownloadQueueSummary()

        assertEquals(40, summary.activeProgress)
    }

    /** Falls back to average task percentages when servers did not declare byte totals. */
    @Test
    fun `active progress falls back to average percentages`() {
        val summary = listOf(
            task(1, DownloadStatus.DOWNLOADING, progress = 20),
            task(2, DownloadStatus.QUEUED, progress = 80),
        ).toDownloadQueueSummary()

        assertEquals(50, summary.activeProgress)
    }

    private fun task(
        id: Long,
        status: DownloadStatus,
        progress: Int = 0,
        downloadedBytes: Long = 0,
        totalBytes: Long = 0,
    ) = DownloadTask(
        id = id,
        title = "Task $id",
        filename = "task-$id.mp4",
        url = "https://example.com/task-$id.mp4",
        status = status,
        progress = progress,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
    )
}