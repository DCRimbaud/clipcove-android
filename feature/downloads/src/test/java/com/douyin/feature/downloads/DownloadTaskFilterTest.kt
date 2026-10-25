package com.douyin.feature.downloads

import com.douyin.data.download.DownloadStatus
import com.douyin.data.download.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadTaskFilterTest {

    private val tasks = listOf(
        task(1, "Cat video", "cat.mp4", DownloadStatus.DONE),
        task(2, "Dog queue", "dog.mp4", DownloadStatus.QUEUED),
        task(3, "Cat downloading", "cat-live.mp4", DownloadStatus.DOWNLOADING),
        task(4, "Paused clip", "paused.mp4", DownloadStatus.PAUSED),
        task(5, "Broken dog", "dog-error.mp4", DownloadStatus.FAILED),
    )

    @Test
    fun `search matches title and filename ignoring case`() {
        val result = DownloadTaskFilter.apply(tasks, "CAT", DownloadFilter.ALL)

        assertEquals(setOf(1L, 3L), result.map { it.id }.toSet())
    }

    @Test
    fun `active filter includes queued downloading and paused`() {
        val result = DownloadTaskFilter.apply(tasks, "", DownloadFilter.ACTIVE)

        assertEquals(setOf(2L, 3L, 4L), result.map { it.id }.toSet())
    }

    @Test
    fun `status and query filters combine`() {
        val result = DownloadTaskFilter.apply(tasks, "dog", DownloadFilter.FAILED)

        assertEquals(listOf(5L), result.map { it.id })
    }

    private fun task(id: Long, title: String, filename: String, status: DownloadStatus) =
        DownloadTask(
            id = id,
            title = title,
            filename = filename,
            url = "https://example.com/$filename",
            status = status,
        )
}