package com.example.video2gif.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.video2gif.domain.JobStatus
import com.example.video2gif.domain.RunItem
import com.example.video2gif.domain.RunState
import com.example.video2gif.domain.formatBytes
import com.example.video2gif.ui.AppViewModel
import com.example.video2gif.ui.components.AppTopBar
import com.example.video2gif.ui.components.BottomGlassBar
import com.example.video2gif.ui.components.MetaText
import com.example.video2gif.ui.components.PrimaryAction
import com.example.video2gif.ui.components.SkeletonBox
import com.example.video2gif.ui.components.StatusPill
import com.example.video2gif.ui.theme.AppTheme

@Composable
fun ProgressScreen(vm: AppViewModel) {
    val run by vm.run.collectAsStateWithLifecycle()
    val state = run
    if (state == null) {
        EmptyProgress(vm)
        return
    }
    var confirmCancelAll by remember { mutableStateOf(false) }
    var barHeightPx by remember { mutableStateOf(0) }
    val bottomPadding = with(LocalDensity.current) { barHeightPx.toDp() } + 12.dp
    val extended = AppTheme.extended
    val overall by animateFloatAsState(
        targetValue = state.totalProgress,
        animationSpec = tween(240),
        label = "overallProgress",
    )
    val finished = !state.isRunning
    val activeItem = state.currentItem

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            AppTopBar(
                title = if (finished) "转换结果" else "正在转换",
                subtitle = "${state.finishedCount} / ${state.items.size} 个视频",
                onBack = { vm.back() },
            )
            Column(
                Modifier
                    .weight(1f)
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = bottomPadding),
            ) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            if (finished) {
                                if (state.failedCount > 0) {
                                    "完成 ${state.succeededCount} 个，失败 ${state.failedCount} 个"
                                } else {
                                    "已完成 ${state.succeededCount} 个"
                                }
                            } else {
                                "正在处理第 ${(state.currentIndex + 1).coerceAtLeast(1)} 个"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${(overall * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFeatureSettings = "tnum",
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { overall },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(
                            "成功 ${state.succeededCount}",
                            container = extended.successContainer,
                            contentColor = extended.onSuccessContainer,
                            icon = Icons.Rounded.CheckCircle,
                        )
                        if (state.failedCount > 0) {
                            StatusPill(
                                "失败 ${state.failedCount}",
                                container = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                icon = Icons.Rounded.ErrorOutline,
                            )
                        }
                        if (state.canceledCount > 0) {
                            StatusPill(
                                "取消 ${state.canceledCount}",
                                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                icon = Icons.Rounded.Block,
                            )
                        }
                        if (state.interruptedCount > 0) {
                            StatusPill(
                                "中断 ${state.interruptedCount}",
                                container = extended.warningContainer,
                                contentColor = extended.onWarningContainer,
                                icon = Icons.Rounded.WarningAmber,
                            )
                        }
                    }
                    if (state.stopReason != null && finished) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.stopReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                }

                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    state.items.forEachIndexed { index, item ->
                        ProgressRow(item = item, isCurrent = index == state.currentIndex)
                        if (index != state.items.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 64.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
                if (finished) {
                    Text(
                        "转换结束后可直接分享。GIF 不包含声音。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }

        BottomGlassBar(Modifier.align(Alignment.BottomCenter).onSizeChanged { barHeightPx = it.height }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!finished) {
                    TextButton(
                        onClick = { vm.cancelCurrentConversion() },
                        enabled = activeItem != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("取消当前项")
                    }
                    OutlinedButton(
                        onClick = { confirmCancelAll = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("取消全部")
                    }
                } else {
                    if (state.hasRetryable) {
                        OutlinedButton(
                            onClick = { vm.retryConversion() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("重试未完成项")
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.width(12.dp))
                    PrimaryAction(
                        text = "查看结果",
                        onClick = { vm.openResult() },
                        enabled = state.items.any { it.status == JobStatus.Succeeded },
                    )
                }
            }
        }
    }

    if (confirmCancelAll) {
        AlertDialog(
            onDismissRequest = { confirmCancelAll = false },
            title = { Text("取消全部转换？") },
            text = { Text("已完成的 GIF 会保留。正在写入的项会被取消，未开始的项不会转换。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCancelAll = false
                        vm.cancelAllConversion()
                    },
                ) { Text("取消全部", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancelAll = false }) { Text("继续转换") }
            },
        )
    }
}

@Composable
private fun ProgressRow(item: RunItem, isCurrent: Boolean) {
    val extended = AppTheme.extended
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrent) {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 40.dp)
                .clip(CircleShape)
                .background(statusColor(item.status).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            when (item.status) {
                JobStatus.Succeeded -> Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = extended.success,
                    modifier = Modifier.size(20.dp),
                )
                JobStatus.Failed -> Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                JobStatus.Canceled -> Icon(
                    Icons.Rounded.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                JobStatus.Interrupted -> Icon(
                    Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = extended.warning,
                    modifier = Modifier.size(20.dp),
                )
                JobStatus.Queued -> Icon(
                    Icons.Rounded.HourglassTop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                else -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when (item.status) {
                    JobStatus.Succeeded -> "已保存 · ${formatBytes(item.outputSizeBytes)}"
                    JobStatus.Failed -> item.error ?: "转换失败"
                    JobStatus.Canceled -> "已取消"
                    JobStatus.Interrupted -> item.error ?: "已中断，可重试"
                    else -> statusText(item.status)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (item.status == JobStatus.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (isCurrent && item.status in listOf(
                    JobStatus.Preparing,
                    JobStatus.Encoding,
                    JobStatus.Publishing,
                )
            ) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
        }
        if (isCurrent && item.status in listOf(
                JobStatus.Preparing,
                JobStatus.Encoding,
                JobStatus.Publishing,
            )
        ) {
            Text(
                "${(item.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun statusColor(status: JobStatus) = when (status) {
    JobStatus.Succeeded -> AppTheme.extended.success
    JobStatus.Failed -> MaterialTheme.colorScheme.error
    JobStatus.Interrupted -> AppTheme.extended.warning
    JobStatus.Canceled -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.primary
}

private fun statusText(status: JobStatus): String = when (status) {
    JobStatus.Queued -> "排队中"
    JobStatus.Preparing -> "正在准备"
    JobStatus.Encoding -> "正在编码"
    JobStatus.Publishing -> "正在保存"
    JobStatus.Succeeded -> "已完成"
    JobStatus.Failed -> "失败"
    JobStatus.Canceled -> "已取消"
    JobStatus.Interrupted -> "已中断"
}

@Composable
private fun EmptyProgress(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("没有进行中的任务", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { vm.goHome() }) { Text("返回首页") }
    }
}
