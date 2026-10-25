package com.douyin.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Downloads a published APK in-app, verifies it, and opens Android's installer. */
class AndroidUpdateInstaller(
    context: Context,
    private val fileProviderAuthority: String,
    private val client: OkHttpClient = defaultClient(),
) : UpdateInstaller {

    private val appContext = context.applicationContext

    override suspend fun downloadAndInstall(
        release: AppRelease,
        onProgress: (Int) -> Unit,
    ): InstallResult = withContext(Dispatchers.IO) {
        if (!canRequestInstall()) {
            openInstallPermissionSettings()
            return@withContext InstallResult.PermissionRequired
        }

        val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: throw UpdateException("该版本未提供 APK 安装包，请打开 GitHub 发布页手动下载")
        val checksumAsset = release.assets.firstOrNull {
            it.name.endsWith(".sha256", ignoreCase = true)
        } ?: throw UpdateException("该版本缺少校验文件，请打开 GitHub 发布页手动下载")
        val expectedHash = fetchChecksum(checksumAsset)
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

    private fun fetchChecksum(asset: ReleaseAsset): String? {
        val request = Request.Builder()
            .url(asset.downloadUrl)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            val text = response.body?.string().orEmpty()
            return UpdatePackageVerifier.parseExpectedSha256(text)
        }
    }

    private suspend fun downloadAsset(
        asset: ReleaseAsset,
        destination: File,
        partFile: File,
        onProgress: (Int) -> Unit,
    ) {
        var attempt = 0
        while (true) {
            try {
                downloadAttempt(asset, destination, partFile, onProgress)
                return
            } catch (error: Exception) {
                if (attempt >= MAX_RETRIES || !isRetryable(error)) throw error
                attempt++
                delay(1_000L * attempt)
            }
        }
    }

    private fun downloadAttempt(
        asset: ReleaseAsset,
        destination: File,
        partFile: File,
        onProgress: (Int) -> Unit,
    ) {
        val startBytes = partFile.length()
        val request = Request.Builder()
            .url(asset.downloadUrl)
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
        const val MAX_RETRIES = 2
        const val USER_AGENT = "ClipCove-Android-Updater"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.MINUTES)
            .followRedirects(true)
            .build()
    }
}

class UpdateException(message: String) : IOException(message)