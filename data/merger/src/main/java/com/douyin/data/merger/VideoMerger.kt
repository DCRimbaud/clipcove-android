/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Defines the media merger port consumed by feature code.
 */
package com.douyin.data.merger

import android.content.Context

/**
 * Video merger contract: combine a live-photo video with an audio track into one MP4.
 * The current adapter uses ffmpeg-kit; callers depend only on this port.
 */
interface VideoMerger {

    /** Starts an asynchronous merge and reports success or a user-readable failure message. */
    fun merge(
        context: Context,
        videoUrl: String,
        audioUrl: String,
        filename: String,
        onResult: (Boolean, String) -> Unit
    )
}