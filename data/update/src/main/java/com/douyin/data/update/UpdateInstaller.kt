/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Defines the update installation port and result contract.
 */
package com.douyin.data.update

/** Terminal result of an update download/install attempt. */
sealed interface InstallResult {
    /** Android's package installer was opened successfully. */
    data object InstallerOpened : InstallResult
    /** The user must grant unknown-source installation permission first. */
    data object PermissionRequired : InstallResult
}

/** Port that downloads, verifies, and opens Android update packages. */
interface UpdateInstaller {
    suspend fun downloadAndInstall(
        release: AppRelease,
        allowMirrors: Boolean,
        onProgress: (Int) -> Unit,
    ): InstallResult
}