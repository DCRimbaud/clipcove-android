/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Defines the download repository contract used by features and services.
 */
package com.douyin.data.download

import kotlinx.coroutines.flow.StateFlow

/**
 * Download repository contract. Features depend only on this port so storage implementations remain replaceable.
 */
interface DownloadRepository {

    /** Task snapshots ordered newest first; observers refresh automatically. */
    val tasks: StateFlow<List<DownloadTask>>

    /** Initializes storage and restoration with an application context. */
    fun init(context: android.content.Context)

    /** Enqueues a download and starts it subject to the global concurrency limit. */
    fun enqueue(title: String, filename: String, url: String): DownloadTask

    /** Pauses a download while retaining its part file for later resume. */
    fun pause(task: DownloadTask)

    /** Resumes a paused task from its persisted byte offset. */
    fun resume(task: DownloadTask)

    /** Removes a task and its private backing files. */
    fun remove(task: DownloadTask)
}