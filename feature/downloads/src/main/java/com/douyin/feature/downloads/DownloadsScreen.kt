/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Renders download filters, task actions, progress, and destructive confirmations.
 */
package com.douyin.feature.downloads

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.douyin.core.designsystem.DyBorder
import com.douyin.core.designsystem.DyCard
import com.douyin.core.designsystem.DyCyan
import com.douyin.core.designsystem.DyEmptyState
import com.douyin.core.designsystem.DyGreen
import com.douyin.core.designsystem.DyIconTile
import com.douyin.core.designsystem.DyMotion
import com.douyin.core.designsystem.DyInput
import com.douyin.core.designsystem.DyPageHeader
import com.douyin.core.designsystem.DyPanel
import com.douyin.core.designsystem.DyPill
import com.douyin.core.designsystem.DyRed
import com.douyin.core.designsystem.DyText
import com.douyin.core.designsystem.DyTextSecondary
import com.douyin.core.designsystem.dyPressable
import com.douyin.data.download.DownloadRepository
import com.douyin.data.download.DownloadStatus
import com.douyin.core.uiresources.R as SharedR
import com.douyin.data.download.DownloadTask
import com.douyin.data.download.toDownloadQueueSummary
import java.io.File

@Composable
/**
 * Observes all download tasks and renders filtering, progress, and task actions.
 *
 * Destructive actions require explicit confirmation. The screen owns only local
 * query/dialog state; repository state remains the source of truth.
 */
fun DownloadsScreen(repository: DownloadRepository) {
    val tasks by repository.tasks.collectAsStateWithLifecycle()
    val activeCount = tasks.count {
        it.status == DownloadStatus.DOWNLOADING ||
            it.status == DownloadStatus.QUEUED ||
            it.status == DownloadStatus.PAUSED
    }
    val completedCount = tasks.count { it.status == DownloadStatus.DONE }
    val failedCount = tasks.count { it.status == DownloadStatus.FAILED }
    val queueSummary = tasks.toDownloadQueueSummary()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(DownloadFilter.ALL) }
    var confirmClearCompleted by remember { mutableStateOf(false) }
    val filteredTasks = DownloadTaskFilter.apply(tasks, query, filter)

    Column(
        modifier = Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 14.dp),
    ) {
        DyPageHeader(
            title = stringResource(SharedR.string.nav_downloads),
            subtitle = stringResource(SharedR.string.downloads_subtitle),
            eyebrow = stringResource(SharedR.string.nav_downloads),
            trailing = {
                DyPill(
                    text = stringResource(SharedR.string.unit_items, tasks.size),
                    color = DyCyan,
                )
            },
        )
        Spacer(Modifier.height(16.dp))

        if (tasks.isEmpty()) {
            DyEmptyState(
                icon = Icons.Filled.Download,
                title = stringResource(SharedR.string.downloads_empty_title),
                description = stringResource(SharedR.string.downloads_empty_description),
            )
        } else {
            DyPanel(contentPadding = 14.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryMetric(
                        stringResource(SharedR.string.downloads_active),
                        activeCount.toString(),
                        DyCyan,
                        Modifier.weight(1f),
                    )
                    SummaryMetric(
                        stringResource(SharedR.string.downloads_completed),
                        completedCount.toString(),
                        DyGreen,
                        Modifier.weight(1f),
                    )
                    SummaryMetric(
                        stringResource(SharedR.string.downloads_all),
                        tasks.size.toString(),
                        DyText,
                        Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text(
                        stringResource(SharedR.string.downloads_search_hint),
                        color = DyTextSecondary,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = DyTextSecondary)
                },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(SharedR.string.action_clear_search),
                                tint = DyTextSecondary,
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DyText,
                    unfocusedTextColor = DyText,
                    focusedBorderColor = DyCyan.copy(alpha = 0.75f),
                    unfocusedBorderColor = DyBorder,
                    focusedContainerColor = DyInput,
                    unfocusedContainerColor = DyInput,
                    cursorColor = DyCyan,
                ),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                DownloadFilter.entries.forEach { option ->
                    FilterChip(
                        label = when (option) {
                            DownloadFilter.ACTIVE -> "${stringResource(SharedR.string.downloads_active)} $activeCount"
                            DownloadFilter.DONE -> "${stringResource(SharedR.string.downloads_completed)} $completedCount"
                            DownloadFilter.FAILED -> "${stringResource(SharedR.string.downloads_failed)} $failedCount"
                            DownloadFilter.ALL -> "${stringResource(SharedR.string.downloads_all)} ${tasks.size}"
                        },
                        selected = filter == option,
                        modifier = Modifier.weight(1f),
                        onClick = { filter = option },
                    )
                }
            }
            AnimatedVisibility(
                visible = queueSummary.hasActive || queueSummary.hasPaused || queueSummary.hasFailed,
                enter = fadeIn(tween(DyMotion.QuickMs)) +
                    expandVertically(animationSpec = tween(DyMotion.QuickMs)),
                exit = fadeOut(tween(DyMotion.QuickMs)) +
                    shrinkVertically(animationSpec = tween(DyMotion.QuickMs)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (queueSummary.hasActive) {
                        QueueAction(
                            text = stringResource(SharedR.string.downloads_pause_all),
                            color = DyCyan,
                            modifier = Modifier.weight(1f),
                            onClick = repository::pauseAll,
                        )
                    }
                    if (queueSummary.hasPaused) {
                        QueueAction(
                            text = stringResource(SharedR.string.downloads_resume_all),
                            color = DyGreen,
                            modifier = Modifier.weight(1f),
                            onClick = repository::resumeAll,
                        )
                    }
                    if (queueSummary.hasFailed) {
                        QueueAction(
                            text = stringResource(SharedR.string.downloads_retry_failed),
                            color = DyRed,
                            modifier = Modifier.weight(1f),
                            onClick = repository::retryFailed,
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = completedCount > 0 && filter == DownloadFilter.DONE,
                enter = fadeIn(tween(DyMotion.StandardMs, easing = DyMotion.StandardEasing)) +
                    expandVertically(
                        animationSpec = tween(
                            DyMotion.StandardMs,
                            easing = DyMotion.StandardEasing,
                        )
                    ),
                exit = fadeOut(tween(DyMotion.QuickMs, easing = DyMotion.StandardEasing)) +
                    shrinkVertically(
                        animationSpec = tween(
                            DyMotion.QuickMs,
                            easing = DyMotion.StandardEasing,
                        )
                    ),
            ) {
                Column {
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { confirmClearCompleted = true }) {
                            Text(
                                stringResource(SharedR.string.downloads_clear_completed),
                                color = DyRed,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            AnimatedContent(
                targetState = filteredTasks.isEmpty(),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    (
                        fadeIn(
                            tween(
                                DyMotion.StandardMs,
                                easing = DyMotion.EmphasizedEasing,
                            )
                        ) + slideInVertically(
                            animationSpec = tween(
                                DyMotion.StandardMs,
                                easing = DyMotion.EmphasizedEasing,
                            ),
                            initialOffsetY = { it / 24 },
                        )
                    ).togetherWith(
                        fadeOut(
                            tween(
                                DyMotion.QuickMs,
                                easing = DyMotion.StandardEasing,
                            )
                        )
                    ).using(SizeTransform(clip = false))
                },
                label = "downloadContent",
            ) { empty ->
                if (empty) {
                    DyPanel(contentPadding = 16.dp) {
                        Text(
                            if (query.isBlank()) {
                                stringResource(SharedR.string.downloads_empty_category)
                            } else {
                                stringResource(SharedR.string.downloads_no_search_results, query)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = DyTextSecondary,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(filteredTasks, key = { it.id }) { task ->
                            DownloadCard(task = task, repository = repository)
                        }
                    }
                }
            }
        }
    }

    if (confirmClearCompleted) {
        AlertDialog(
            onDismissRequest = { confirmClearCompleted = false },
            containerColor = DyCard,
            title = {
                Text(
                    stringResource(SharedR.string.downloads_clear_dialog_title),
                    color = DyText,
                )
            },
            text = {
                Text(
                    stringResource(
                        SharedR.string.downloads_clear_dialog_description,
                        completedCount,
                    ),
                    color = DyTextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearCompleted = false
                        tasks.filter { it.status == DownloadStatus.DONE }.forEach(repository::remove)
                        if (filter == DownloadFilter.DONE) filter = DownloadFilter.ALL
                    },
                ) { Text(stringResource(SharedR.string.action_clear), color = DyRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCompleted = false }) {
                    Text(stringResource(SharedR.string.action_cancel), color = DyTextSecondary)
                }
            },
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) DyCyan.copy(alpha = 0.15f) else DyInput,
        animationSpec = tween(DyMotion.StandardMs, easing = DyMotion.StandardEasing),
        label = "downloadFilterColor",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) DyCyan.copy(alpha = 0.65f) else DyBorder,
        animationSpec = tween(DyMotion.StandardMs, easing = DyMotion.StandardEasing),
        label = "downloadFilterBorder",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) DyCyan else DyTextSecondary,
        animationSpec = tween(DyMotion.QuickMs, easing = DyMotion.StandardEasing),
        label = "downloadFilterLabel",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        animationSpec = tween(DyMotion.StandardMs, easing = DyMotion.StandardEasing),
        label = "downloadFilterScale",
    )
    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .dyPressable(pressedScale = 0.96f, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, color = DyTextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DownloadCard(task: DownloadTask, repository: DownloadRepository) {
    val context = LocalContext.current
    val statusColor by animateColorAsState(
        targetValue = task.status.statusColor(),
        animationSpec = tween(DyMotion.StandardMs, easing = DyMotion.StandardEasing),
        label = "downloadStatusColor",
    )
    val animatedProgress by animateFloatAsState(
        targetValue = (task.progress / 100f).coerceIn(0f, 1f),
        animationSpec = tween(DyMotion.StandardMs, easing = DyMotion.StandardEasing),
        label = "downloadProgress",
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(
                    DyMotion.StandardMs,
                    easing = DyMotion.StandardEasing,
                )
            ),
        shape = RoundedCornerShape(20.dp),
        color = DyCard,
        border = BorderStroke(1.dp, DyBorder.copy(alpha = 0.68f)),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                DyIconTile(icon = Icons.Filled.Download, tint = statusColor, size = 42.dp)
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium, color = DyText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(task.filename, style = MaterialTheme.typography.bodySmall, color = DyTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DyPill(task.status.label(), statusColor)
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                color = statusColor,
                trackColor = DyInput,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(downloadDetail(task), style = MaterialTheme.typography.bodySmall, color = DyTextSecondary, modifier = Modifier.weight(1f))
                if (task.totalBytes > 0) {
                    Text(formatSize(task.totalBytes), style = MaterialTheme.typography.bodySmall, color = DyTextSecondary)
                }
            }
            Spacer(Modifier.height(8.dp))
            DownloadActions(task = task, repository = repository, context = context)
        }
    }
}

@Composable
private fun DownloadActions(task: DownloadTask, repository: DownloadRepository, context: Context) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        when (task.status) {
            DownloadStatus.DOWNLOADING -> {
                TaskAction(stringResource(SharedR.string.action_pause), DyCyan, Modifier.weight(1f)) {
                    repository.pause(task)
                }
                TaskAction(stringResource(SharedR.string.action_cancel), DyRed, Modifier.weight(1f)) {
                    repository.remove(task)
                }
            }
            DownloadStatus.PAUSED -> {
                TaskAction(stringResource(SharedR.string.action_resume), DyGreen, Modifier.weight(1f)) {
                    repository.resume(task)
                }
                TaskAction(stringResource(SharedR.string.action_delete), DyRed, Modifier.weight(1f)) {
                    repository.remove(task)
                }
            }
            DownloadStatus.FAILED -> {
                TaskAction(stringResource(SharedR.string.action_retry), DyCyan, Modifier.weight(1f)) {
                    repository.retry(task)
                }
                TaskAction(stringResource(SharedR.string.action_delete), DyRed, Modifier.weight(1f)) {
                    repository.remove(task)
                }
            }
            DownloadStatus.QUEUED -> {
                TaskAction(stringResource(SharedR.string.action_cancel), DyRed, Modifier.weight(1f)) {
                    repository.remove(task)
                }
            }
            DownloadStatus.DONE -> {
                TaskAction(stringResource(SharedR.string.action_open), DyText, Modifier.weight(1f)) {
                    openDownloaded(context, task)
                }
                TaskAction(stringResource(SharedR.string.action_share), DyText, Modifier.weight(1f)) {
                    shareDownloaded(context, task)
                }
                TaskAction(stringResource(SharedR.string.action_save_as), DyText, Modifier.weight(1f)) {
                    saveToDownloads(context, task)
                }
                TaskAction(stringResource(SharedR.string.action_delete), DyRed, Modifier.weight(1f)) {
                    repository.remove(task)
                }
            }
        }
    }
}

@Composable
private fun TaskAction(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun QueueAction(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun downloadDetail(task: DownloadTask): String = when (task.status) {
    DownloadStatus.QUEUED -> stringResource(SharedR.string.downloads_status_queued_detail)
    DownloadStatus.DOWNLOADING -> "${task.progress}% · ${formatSpeed(task.speedBps)}"
    DownloadStatus.PAUSED -> stringResource(
        SharedR.string.downloads_status_paused_detail,
        task.progress,
    )
    DownloadStatus.DONE -> stringResource(SharedR.string.downloads_status_done_detail)
    DownloadStatus.FAILED -> stringResource(SharedR.string.downloads_status_failed_detail)
}

@Composable
private fun DownloadStatus.label(): String = when (this) {
    DownloadStatus.QUEUED -> stringResource(SharedR.string.downloads_status_queued)
    DownloadStatus.DOWNLOADING -> stringResource(SharedR.string.downloads_status_downloading)
    DownloadStatus.PAUSED -> stringResource(SharedR.string.downloads_status_paused)
    DownloadStatus.DONE -> stringResource(SharedR.string.downloads_status_done)
    DownloadStatus.FAILED -> stringResource(SharedR.string.downloads_status_failed)
}

private fun DownloadStatus.statusColor(): Color = when (this) {
    DownloadStatus.QUEUED, DownloadStatus.PAUSED, DownloadStatus.DOWNLOADING -> DyCyan
    DownloadStatus.DONE -> DyGreen
    DownloadStatus.FAILED -> DyRed
}

private fun openDownloaded(context: Context, task: DownloadTask) {
    try {
        val uri = taskFileUri(context, task) ?: throw IllegalStateException()
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, guessMime(task.filename))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(SharedR.string.action_open))
        )
    } catch (_: Exception) {
        Toast.makeText(
            context,
            context.getString(SharedR.string.downloads_open_failed),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

private fun shareDownloaded(context: Context, task: DownloadTask) {
    try {
        val uri = taskFileUri(context, task) ?: throw IllegalStateException()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = guessMime(task.filename)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(SharedR.string.action_share))
        )
    } catch (_: Exception) {
        Toast.makeText(
            context,
            context.getString(SharedR.string.downloads_share_failed),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

@Suppress("DEPRECATION")
private fun saveToDownloads(context: Context, task: DownloadTask) {
    if (Build.VERSION.SDK_INT < 29 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        (context as? android.app.Activity)?.requestPermissions(
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            REQUEST_WRITE_STORAGE,
        )
        Toast.makeText(
            context,
            context.getString(SharedR.string.downloads_storage_permission),
            Toast.LENGTH_LONG,
        ).show()
        return
    }
    Thread {
        try {
            val src = File(task.filePath)
            if (!src.exists()) throw IllegalStateException()
            if (Build.VERSION.SDK_INT >= 29) {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, task.filename)
                    put(MediaStore.Downloads.MIME_TYPE, guessMime(task.filename))
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException()
                val out = context.contentResolver.openOutputStream(uri) ?: throw IllegalStateException()
                out.use { output -> src.inputStream().use { it.copyTo(output) } }
            } else {
                val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), task.filename)
                src.copyTo(dest, overwrite = true)
            }
            runOnUiThread(context) {
                Toast.makeText(
                    context,
                    context.getString(SharedR.string.downloads_saved),
                    Toast.LENGTH_LONG,
                ).show()
            }
        } catch (_: Exception) {
            runOnUiThread(context) {
                Toast.makeText(
                    context,
                    context.getString(SharedR.string.downloads_save_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }.start()
}

private fun runOnUiThread(context: Context, block: () -> Unit) {
    (context as? android.app.Activity)?.runOnUiThread { block() } ?: block()
}

private fun taskFileUri(context: Context, task: DownloadTask): Uri? {
    val privateFile = task.filePath.takeIf { it.isNotBlank() }?.let(::File)
    if (privateFile?.isFile == true) {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            privateFile,
        )
    }
    return queryDownloadUri(context, task.filename)
}

private fun queryDownloadUri(context: Context, filename: String): Uri? {
    if (Build.VERSION.SDK_INT < 29) {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    return queryMediaStoreDownload(context, filename)
}

@androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
private fun queryMediaStoreDownload(context: Context, filename: String): Uri? {
    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    val selection = "${MediaStore.Downloads.DISPLAY_NAME}=?"
    context.contentResolver.query(collection, null, selection, arrayOf(filename), "date_added DESC")?.use { cursor ->
        if (cursor.moveToFirst()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
            return Uri.withAppendedPath(collection, id.toString())
        }
    }
    return null
}

private fun guessMime(filename: String): String = when (filename.substringAfterLast('.', "mp4")) {
    "mp3" -> "audio/mpeg"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    else -> "video/mp4"
}

private const val REQUEST_WRITE_STORAGE = 201

private fun formatSpeed(bps: Long): String {
    if (bps <= 0) return "--"
    return if (bps >= 1024 * 1024) {
        "%.1f MB/s".format(bps / 1024.0 / 1024.0)
    } else if (bps >= 1024) {
        "%.0f KB/s".format(bps / 1024.0)
    } else {
        "$bps B/s"
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "--"
    return if (bytes >= 1024L * 1024L * 1024L) {
        "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    } else if (bytes >= 1024L * 1024L) {
        "%.1f MB".format(bytes / 1024.0 / 1024.0)
    } else if (bytes >= 1024L) {
        "%.0f KB".format(bytes / 1024.0)
    } else {
        "$bytes B"
    }
}