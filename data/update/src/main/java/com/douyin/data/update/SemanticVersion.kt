/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Parses and compares ClipCove semantic versions including release candidates.
 */
package com.douyin.data.update

/** Numeric semantic version used for update comparisons. */
data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
) : Comparable<SemanticVersion> {

    override fun compareTo(other: SemanticVersion): Int {
        val core = compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)
        if (core != 0) return core
        return when {
            preRelease == null && other.preRelease == null -> 0
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> preRelease.compareTo(other.preRelease)
        }
    }

    companion object {
        private val pattern = Regex(
            """(?i)^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$"""
        )

        /** Parses a leading semantic version, including optional release-candidate suffixes. */
    fun parse(value: String): SemanticVersion? {
            val match = pattern.matchEntire(value.trim()) ?: return null
            return SemanticVersion(
                major = match.groupValues[1].toIntOrNull() ?: return null,
                minor = match.groupValues[2].toIntOrNull() ?: return null,
                patch = match.groupValues[3].toIntOrNull() ?: return null,
                preRelease = match.groupValues[4].ifBlank { null },
            )
        }
    }
}