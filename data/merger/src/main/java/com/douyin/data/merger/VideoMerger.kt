package com.douyin.data.merger

import android.content.Context

/**
 * 视频合成接口:动图画面 + 音乐音轨 → 合成 mp4。
 * 当前实现基于 ffmpeg-kit;可替换实现不影响上层。
 */
interface VideoMerger {

    /** 异步合成,完成后回调 onResult(success, message) */
    fun merge(
        context: Context,
        videoUrl: String,
        audioUrl: String,
        filename: String,
        onResult: (Boolean, String) -> Unit
    )
}
