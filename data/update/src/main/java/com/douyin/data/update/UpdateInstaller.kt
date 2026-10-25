package com.douyin.data.update

sealed interface InstallResult {
    data object InstallerOpened : InstallResult
    data object PermissionRequired : InstallResult
}

interface UpdateInstaller {
    suspend fun downloadAndInstall(
        release: AppRelease,
        allowMirrors: Boolean,
        onProgress: (Int) -> Unit,
    ): InstallResult
}