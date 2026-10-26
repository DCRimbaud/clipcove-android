/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Downloads, probes, verifies, and opens Android APK updates without weakening integrity checks.
 */
package com.douyin.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Downloads a published APK in-app, verifies it, and opens Android's installer. */
class AndroidUpdateInstaller(
    context: Context,
    private val fileProviderAuthority: String,
    private val client: OkHttpClient = defaultClient(),
) : UpdateInstaller {

    private val appContext = context.applicationContext

    private val probeClient by lazy {
        client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(7, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private val checksumClient by lazy {
        client.newBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val chunkClient by lazy {
        client.newBuilder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.MINUTES)
            .build()
    }

    override suspend fun downloadAndInstall(
        release: AppRelease,
        allowMirrors: Boolean,
        onProgress: (Int) -> Unit,
    ): InstallResult = withContext(Dispatchers.IO) {
        if (!canRequestInstall()) {
            openInstallPermissionSettings()
            return@withContext InstallResult.PermissionRequired
        }

        val apkAsset = UpdateAssetSelector.selectApk(release.assets, Build.SUPPORTED_ABIS.toList())
            ?: throw UpdateException("该版本没有适用于当前设备的安装包，请打开 GitHub 发布页手动下载")
        val checksumAsset = UpdateAssetSelector.checksumFor(release.assets, apkAsset)
            ?: throw UpdateException("该版本缺少对应安装包的校验文件，请打开 GitHub 发布页手动下载")
        val expectedHash = fetchChecksum(checksumAsset, allowMirrors)
            ?: throw UpdateException("更新包校验文件无效，请稍后重试")

        val updateDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updateDir, safeApkName(release.tagName))
        val partFile = File(updateDir, apkFile.name + ".part")
        cleanupOldUpdates(updateDir, setOf(apkFile.name, partFile.name))
        ensureStorageAvailable(updateDir, apkAsset.sizeBytes, partFile.length())

        val reusable = apkFile.isFile && apkFile.length() > 0 &&
            (apkAsset.sizeBytes <= 0 || apkFile.length() == apkAsset.sizeBytes)
        if (reusable) {
            onProgress(100)
        } else {
            downloadAsset(
                asset = apkAsset,
                destination = apkFile,
                partFile = partFile,
                allowMirrors = allowMirrors,
                expectedHash = expectedHash,
                onProgress = onProgress,
            )
        }

        if (apkAsset.sizeBytes > 0 && apkFile.length() != apkAsset.sizeBytes) {
            apkFile.delete()
            throw UpdateException("下载文件不完整，请重试")
        }
        if (UpdatePackageVerifier.sha256(apkFile) != expectedHash) {
            apkFile.delete()
            partFile.delete()
            throw UpdateException("更新包 SHA-256 校验失败，请重试")
        }

        validateApkPackage(apkFile, release)
        openInstaller(apkFile)
    }

    private fun canRequestInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            appContext.packageManager.canRequestPackageInstalls()

    private fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${appContext.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    private fun ensureStorageAvailable(directory: File, expectedBytes: Long, existingPartBytes: Long) {
        if (expectedBytes <= 0) return
        val remaining = (expectedBytes - existingPartBytes).coerceAtLeast(0)
        val reserve = 16L * 1024L * 1024L
        val available = StatFs(directory.absolutePath).availableBytes
        if (available < remaining + reserve) {
            throw UpdateException("手机存储空间不足，无法下载更新")
        }
    }

    private fun cleanupOldUpdates(directory: File, keepNames: Set<String>) {
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in keepNames) file.delete()
        }
    }

    private fun fetchChecksum(asset: ReleaseAsset, allowMirrors: Boolean): String? {
        var lastError: Exception? = null
        for (url in UpdateDownloadRoutes.candidates(asset.downloadUrl, allowMirrors)) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()
                checksumClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw HttpStatusException(response.code)
                    val text = response.body?.string().orEmpty()
                    return UpdatePackageVerifier.parseExpectedSha256(text)
                }
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: UpdateException("无法下载更新校验文件")
    }

    private suspend fun downloadAsset(
        asset: ReleaseAsset,
        destination: File,
        partFile: File,
        allowMirrors: Boolean,
        expectedHash: String,
        onProgress: (Int) -> Unit,
    ) {
        var lastError: Exception? = null
        val giteeManifest = UpdateDownloadRoutes.giteeChunkManifest(asset.downloadUrl)
        if (allowMirrors && giteeManifest != null) {
            try {
                downloadGiteeChunks(
                    asset = asset,
                    manifestUrl = giteeManifest,
                    destination = destination,
                    partFile = partFile,
                    expectedHash = expectedHash,
                    onProgress = onProgress,
                )
                cleanupGiteeChunks(partFile)
                return
            } catch (error: Exception) {
                lastError = error
                partFile.delete()
                Log.w(TAG, "Gitee chunk mirror unavailable; falling back", error)
            }
        }
        val routes = orderRoutes(UpdateDownloadRoutes.candidates(asset.downloadUrl, allowMirrors))
        for (url in routes) {
            var attempt = 0
            while (attempt <= MAX_RETRIES_PER_SOURCE) {
                try {
                    downloadAttempt(asset, url, destination, partFile, onProgress)
                    cleanupGiteeChunks(partFile)
                    return
                } catch (error: Exception) {
                    lastError = error
                    if (attempt >= MAX_RETRIES_PER_SOURCE || !isRetryable(error)) break
                    attempt++
                    delay(1_000L * attempt)
                }
            }
        }
        throw UpdateException(
            "所有下载线路均失败，请切换网络后重试，或在设置中开启更新加速线路"
        ).also { error -> lastError?.let(error::initCause) }
    }

    private suspend fun downloadGiteeChunks(
        asset: ReleaseAsset,
        manifestUrl: String,
        destination: File,
        partFile: File,
        expectedHash: String,
        onProgress: (Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val manifest = fetchGiteeManifest(manifestUrl, asset, expectedHash)
        val chunkDir = File(partFile.parentFile, partFile.name + ".gitee-parts").apply { mkdirs() }
        val existingBytes = manifest.parts.sumOf { part ->
            File(chunkDir, part.name).takeIf { it.isFile && it.length() == part.sizeBytes }?.length() ?: 0L
        }
        ensureStorageAvailable(chunkDir, (manifest.totalBytes - existingBytes) + manifest.totalBytes, 0L)
        val completedBytes = AtomicLong(existingBytes)
        val lastProgress = AtomicInteger(-1)
        val semaphore = Semaphore(GITEE_CHUNK_PARALLEL)

        coroutineScope {
            manifest.parts.map { part ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        downloadGiteeChunk(
                            part = part,
                            manifestUrl = manifestUrl,
                            chunkDir = chunkDir,
                            totalBytes = manifest.totalBytes,
                            completedBytes = completedBytes,
                            lastProgress = lastProgress,
                            onProgress = onProgress,
                        )
                    }
                }
            }.awaitAll()
        }

        assembleGiteeChunks(manifest.parts, chunkDir, partFile)
        if (partFile.length() != manifest.totalBytes) {
            throw IOException("Gitee chunk total size mismatch")
        }
        if (UpdatePackageVerifier.sha256(partFile) != expectedHash) {
            partFile.delete()
            throw IOException("Gitee chunk checksum mismatch")
        }
        movePartToDestination(partFile, destination)
        onProgress(100)
    }

    private fun fetchGiteeManifest(
        manifestUrl: String,
        asset: ReleaseAsset,
        expectedHash: String,
    ): GiteeChunkManifest {
        val request = Request.Builder()
            .url(manifestUrl)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        val raw = probeClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            response.body?.string().orEmpty()
        }
        if (raw.isBlank()) throw IOException("Empty Gitee chunk manifest")
        return parseGiteeManifest(raw, manifestUrl, asset, expectedHash)
    }

    private fun parseGiteeManifest(
        raw: String,
        manifestUrl: String,
        asset: ReleaseAsset,
        expectedHash: String,
    ): GiteeChunkManifest {
        val root = JSONObject(raw)
        if (root.optInt("format") != GITEE_MANIFEST_FORMAT) {
            throw IOException("Unsupported Gitee chunk manifest")
        }
        if (!root.optString("asset").equals(asset.name, ignoreCase = true)) {
            throw IOException("Gitee manifest asset name mismatch")
        }
        val totalBytes = root.optLong("size")
        if (totalBytes <= 0 || (asset.sizeBytes > 0 && totalBytes != asset.sizeBytes)) {
            throw IOException("Gitee manifest size mismatch")
        }
        val manifestHash = root.optString("sha256")
        if (!manifestHash.equals(expectedHash, ignoreCase = true)) {
            throw IOException("Gitee manifest checksum mismatch")
        }
        val array = root.optJSONArray("parts") ?: throw IOException("Missing Gitee chunks")
        val parts = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.optString("name")
                val size = item.optLong("size")
                if (UpdateDownloadRoutes.giteeChunkUrl(manifestUrl, name) == null || size <= 0) {
                    throw IOException("Invalid Gitee chunk entry")
                }
                add(GiteeChunkPart(name = name, sizeBytes = size))
            }
        }
        if (parts.isEmpty() || parts.map { it.name }.distinct().size != parts.size) {
            throw IOException("Invalid Gitee chunk list")
        }
        if (parts.sumOf { it.sizeBytes } != totalBytes) {
            throw IOException("Gitee chunk sizes do not add up")
        }
        return GiteeChunkManifest(totalBytes = totalBytes, parts = parts)
    }

    private fun downloadGiteeChunk(
        part: GiteeChunkPart,
        manifestUrl: String,
        chunkDir: File,
        totalBytes: Long,
        completedBytes: AtomicLong,
        lastProgress: AtomicInteger,
        onProgress: (Int) -> Unit,
    ) {
        val target = File(chunkDir, part.name)
        if (target.isFile && target.length() == part.sizeBytes) return
        if (target.exists()) target.delete()

        val url = UpdateDownloadRoutes.giteeChunkUrl(manifestUrl, part.name)
            ?: throw IOException("Unsafe Gitee chunk URL")
        var lastError: Exception? = null
        repeat(GITEE_CHUNK_ATTEMPTS) { attempt ->
            try {
                downloadGiteeChunkOnce(
                    url = url,
                    target = target,
                    expectedBytes = part.sizeBytes,
                )
                val done = completedBytes.addAndGet(part.sizeBytes)
                val percent = ((done * 100L) / totalBytes).toInt().coerceIn(0, 100)
                if (lastProgress.getAndSet(percent) != percent) onProgress(percent)
                return
            } catch (error: Exception) {
                lastError = error
                target.delete()
                if (attempt + 1 < GITEE_CHUNK_ATTEMPTS) Thread.sleep(500L * (attempt + 1))
            }
        }
        throw lastError ?: IOException("Gitee chunk download failed")
    }

    private fun downloadGiteeChunkOnce(
        url: String,
        target: File,
        expectedBytes: Long,
    ) {
        val temp = File(target.parentFile, target.name + ".download")
        temp.delete()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        try {
            chunkClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw HttpStatusException(response.code)
                val body = response.body ?: throw IOException("Empty Gitee chunk body")
                FileOutputStream(temp, false).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
            if (temp.length() != expectedBytes) throw IOException("Gitee chunk size mismatch")
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } finally {
            temp.delete()
        }
    }

    private fun assembleGiteeChunks(parts: List<GiteeChunkPart>, chunkDir: File, partFile: File) {
        FileOutputStream(partFile, false).use { output ->
            parts.forEach { part ->
                File(chunkDir, part.name).inputStream().use { input -> input.copyTo(output) }
            }
        }
    }

    private fun cleanupGiteeChunks(partFile: File) {
        File(partFile.parentFile, partFile.name + ".gitee-parts").deleteRecursively()
    }

    private suspend fun orderRoutes(routes: List<String>): List<String> {
        if (routes.size <= 1) return routes

        val probes = coroutineScope {
            routes.map { route ->
                async(Dispatchers.IO) { probeRoute(route) }
            }.awaitAll()
        }
        // Rank by measured transfer throughput, not just time-to-first-byte.
        // A low-latency route can still be throttled to a fraction of a faster route.
        val reachable = probes
            .filter { it.reachable }
            .sortedWith(compareByDescending<ProbeResult> { it.throughputBps }.thenBy { it.latencyMs })
        val unreachable = probes.filterNot { it.reachable }
        val ordered = (reachable + unreachable).map { it.url }
        Log.d(
            TAG,
            "update routes: " + probes.joinToString {
                val host = it.url.substringAfter("://").substringBefore('/')
                if (it.reachable) {
                    "$host:${it.throughputBps / 1024}KB/s/${it.latencyMs}ms"
                } else {
                    "$host:offline"
                }
            },
        )
        return ordered
    }

    private fun probeRoute(url: String): ProbeResult {
        val startedAt = System.nanoTime()
        var firstByteAt = 0L
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Range", "bytes=0-${PROBE_BYTES - 1}")
                .get()
                .build()
            probeClient.newCall(request).execute().use { response ->
                val body = response.body
                var bytesRead = 0L
                if (response.isSuccessful && body != null) {
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        val deadline = startedAt + PROBE_WINDOW_MS * 1_000_000
                        while (bytesRead < PROBE_BYTES && System.nanoTime() < deadline) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read > 0 && firstByteAt == 0L) firstByteAt = System.nanoTime()
                            bytesRead += read
                        }
                    }
                }
                val finishedAt = System.nanoTime()
                val transferNanos = (finishedAt - (firstByteAt.takeIf { it > 0 } ?: startedAt))
                    .coerceAtLeast(1L)
                ProbeResult(
                    url = url,
                    reachable = bytesRead > 0,
                    latencyMs = ((firstByteAt.takeIf { it > 0 } ?: finishedAt) - startedAt) / 1_000_000,
                    throughputBps = if (bytesRead > 0) bytesRead * 1_000_000_000L / transferNanos else 0L,
                )
            }
        } catch (error: Exception) {
            ProbeResult(url = url, reachable = false, latencyMs = 0, throughputBps = 0)
        }
    }

    private data class ProbeResult(
        val url: String,
        val reachable: Boolean,
        val latencyMs: Long,
        val throughputBps: Long,
    )

    /** One publicly readable Gitee chunk referenced by a signed release asset manifest. */
    private data class GiteeChunkPart(val name: String, val sizeBytes: Long)

    /** Validated chunk total returned by the Gitee mirror. */
    private data class GiteeChunkManifest(val totalBytes: Long, val parts: List<GiteeChunkPart>)

    private fun downloadAttempt(
        asset: ReleaseAsset,
        downloadUrl: String,
        destination: File,
        partFile: File,
        onProgress: (Int) -> Unit,
    ) {
        val startBytes = partFile.length()
        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", USER_AGENT)
            .apply { if (startBytes > 0) header("Range", "bytes=$startBytes-") }
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 416) {
                if (asset.sizeBytes > 0 && startBytes == asset.sizeBytes) {
                    movePartToDestination(partFile, destination)
                    onProgress(100)
                    return
                }
                partFile.delete()
                throw HttpStatusException(416)
            }
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            val body = response.body ?: throw IOException("Empty response body")
            val resuming = response.code == 206 && startBytes > 0
            if (!resuming) partFile.delete()

            val total = asset.sizeBytes.takeIf { it > 0 }
                ?: parseTotalBytes(response.header("Content-Range"))
                ?: body.contentLength().takeIf { it > 0 }
                ?: 0L
            var downloaded = if (resuming) startBytes else 0L
            var lastProgress = -1

            FileOutputStream(partFile, resuming).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val progress = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
        }

        movePartToDestination(partFile, destination)
        onProgress(100)
    }

    private fun movePartToDestination(partFile: File, destination: File) {
        if (destination.exists()) destination.delete()
        if (!partFile.renameTo(destination)) {
            partFile.copyTo(destination, overwrite = true)
            partFile.delete()
        }
    }

    private fun parseTotalBytes(contentRange: String?): Long? {
        if (contentRange.isNullOrBlank()) return null
        return Regex("""bytes\s+\d+-\d+/(\d+)""")
            .find(contentRange)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
    }

    private fun isRetryable(error: Exception): Boolean = when (error) {
        is HttpStatusException -> error.code >= 500 || error.code == 408 || error.code == 429
        is IOException -> true
        else -> false
    }

    private fun validateApkPackage(apkFile: File, release: AppRelease) {
        val flags = signatureFlags()
        val installed = installedPackageInfo(flags)
        val archive = archivePackageInfo(apkFile, flags)
            ?: throw UpdateException("无法读取更新包信息，请手动安装")
        if (archive.packageName != appContext.packageName) {
            throw UpdateException("更新包应用标识不匹配，已停止安装")
        }
        val expectedVersion = release.tagName.removePrefix("v").trim()
        if (archive.versionName != expectedVersion) {
            throw UpdateException("更新包版本与发布记录不一致，已停止安装")
        }
        if (archiveVersionCode(archive) <= archiveVersionCode(installed)) {
            throw UpdateException("更新包版本号没有提升，已停止安装")
        }
        val installedSigners = certificateDigests(installed)
        val archiveSigners = certificateDigests(archive)
        if (installedSigners.isEmpty() || archiveSigners.isEmpty() || installedSigners != archiveSigners) {
            throw UpdateException("更新包签名与当前版本不一致，请打开 GitHub 发布页手动安装")
        }
    }

    private fun signatureFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    private fun installedPackageInfo(flags: Int): PackageInfo = if (Build.VERSION.SDK_INT >= 33) {
        appContext.packageManager.getPackageInfo(
            appContext.packageName,
            PackageManager.PackageInfoFlags.of(flags.toLong()),
        )
    } else {
        @Suppress("DEPRECATION")
        appContext.packageManager.getPackageInfo(appContext.packageName, flags)
    }

    private fun archivePackageInfo(apkFile: File, flags: Int): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(flags.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
        }

    private fun archiveVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    private fun certificateDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            info.signatures.orEmpty()
        }
        return signatures.mapTo(linkedSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }
    }

    private fun openInstaller(apkFile: File): InstallResult {
        val apkUri = FileProvider.getUriForFile(appContext, fileProviderAuthority, apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(installIntent)
        return InstallResult.InstallerOpened
    }

    private fun safeApkName(tag: String): String =
        "clipcove-${tag.replace(Regex("""[^A-Za-z0-9._-]"""), "_")}.apk"

    private class HttpStatusException(val code: Int) : IOException("HTTP $code")

    private companion object {
        const val MAX_RETRIES_PER_SOURCE = 1
        const val PROBE_BYTES = 384 * 1024
        const val PROBE_WINDOW_MS = 2_500L
        const val GITEE_CHUNK_PARALLEL = 4
        const val GITEE_CHUNK_ATTEMPTS = 2
        const val GITEE_MANIFEST_FORMAT = 1
        const val TAG = "ClipCoveUpdate"
        const val USER_AGENT = "ClipCove-Android-Updater"

        /** Creates the long-lived client used for checksum and APK streaming. */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.MINUTES)
            .followRedirects(true)
            .build()
    }
}

/** User-actionable update failure exposed to the settings state holder. */
class UpdateException(message: String) : IOException(message)
