package com.example.video2gif.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.video2gif.domain.MAX_BATCH_ITEMS
import com.example.video2gif.ui.AppViewModel
import com.example.video2gif.ui.Screen
import com.example.video2gif.ui.components.MetaText
import com.example.video2gif.ui.components.PrimaryAction
import com.example.video2gif.ui.components.SkeletonBox
import com.example.video2gif.ui.components.StatusPill
import com.example.video2gif.ui.theme.AppTheme

@Composable
fun HomeScreen(vm: AppViewModel) {
    val draft = vm.draft
    val run by vm.run.collectAsStateWithLifecycle()
    var confirmReplace by remember { mutableStateOf(false) }
    val remaining = (MAX_BATCH_ITEMS - (draft?.items?.size ?: 0)).coerceAtLeast(1)
    val pickMultiple = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(remaining.coerceAtLeast(2)),
    ) { uris -> if (uris.isNotEmpty()) vm.importVideos(uris) }
    val pickSingle = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) vm.importVideos(listOf(uri)) }
    val launchPicker = {
        if (remaining == 1) {
            pickSingle.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        } else {
            pickMultiple.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
            )
        }
    }
    val extended = AppTheme.extended

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { vm.navigate(Screen.Settings) },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                "视频转 GIF",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "先预览效果，确认后再转换。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(28.dp))

            if (draft == null) {
                EmptyStage()
            } else {
                DraftCard(
                    vm = vm,
                    title = "继续上次调整",
                    subtitle = "${draft.items.size} 个视频 · 参数已保留",
                    onClick = { vm.continueDraft() },
                )
            }

            if (vm.isImporting) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        "正在读取视频信息…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val currentRun = run
            if (currentRun?.isRunning == true) {
                Spacer(Modifier.height(12.dp))
                RunInProgressCard(
                    vm = vm,
                    onClick = { vm.openProgress() },
                )
            } else if (currentRun != null && currentRun.hasRetryable) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(extended.warningContainer)
                        .clickable { vm.openProgress() }
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = extended.onWarningContainer,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "上次转换未完成",
                                style = MaterialTheme.typography.titleSmall,
                                color = extended.onWarningContainer,
                            )
                            Text(
                                "完成 ${currentRun.succeededCount} 个 · ${currentRun.failedCount + currentRun.interruptedCount + currentRun.canceledCount} 个可重试",
                                style = MaterialTheme.typography.bodySmall,
                                color = extended.onWarningContainer.copy(alpha = 0.8f),
                            )
                        }
                        Icon(
                            Icons.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = extended.onWarningContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else if (currentRun != null && currentRun.succeededCount > 0) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(extended.successContainer)
                        .clickable { vm.openResult() }
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = extended.onSuccessContainer,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (currentRun.failedCount > 0) {
                                    "上次完成 ${currentRun.succeededCount} 个，失败 ${currentRun.failedCount} 个"
                                } else {
                                    "上次完成 ${currentRun.succeededCount} 个 GIF"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = extended.onSuccessContainer,
                            )
                            Text(
                                "查看结果与分享",
                                style = MaterialTheme.typography.bodySmall,
                                color = extended.onSuccessContainer.copy(alpha = 0.8f),
                            )
                        }
                        Icon(
                            Icons.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = extended.onSuccessContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            PrimaryAction(
                text = "选择视频",
                onClick = {
                    if (draft != null) confirmReplace = true else launchPicker()
                },
                icon = Icons.Rounded.Add,
                enabled = !vm.isImporting,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "每批最多 $MAX_BATCH_ITEMS 个 · 在设备上处理",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("开始新的批次？") },
            text = { Text("当前草稿会被替换。已完成的结果仍保留在结果页。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReplace = false
                        vm.discardDraft()
                        launchPicker()
                    },
                ) { Text("替换草稿") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReplace = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun EmptyStage() {
    val extended = AppTheme.extended
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(width = 168.dp, height = 118.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 132.dp, height = 88.dp)
                    .offset(x = 18.dp, y = 12.dp)
                    .rotate(7f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            Box(
                Modifier
                    .size(width = 132.dp, height = 88.dp)
                    .offset(x = (-14).dp, y = 16.dp)
                    .rotate(-6f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            )
            Box(
                modifier = Modifier
                    .size(width = 142.dp, height = 94.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(extended.mediaStage)
                    .border(1.dp, extended.mediaStageBorder, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Movie,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.82f),
                    modifier = Modifier.size(32.dp),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        "GIF",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "还没有选择视频",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "支持常见手机录像格式，声音会被忽略",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DraftCard(
    vm: AppViewModel,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val batch = vm.draft ?: return
    val first = batch.items.firstOrNull { it.source != null } ?: batch.items.firstOrNull()
    val thumb = first?.let { vm.thumbnails[it.id] }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                SkeletonBox(Modifier.fillMaxSize(), RoundedCornerShape(10.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Rounded.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun RunInProgressCard(vm: AppViewModel, onClick: () -> Unit) {
    val run by vm.run.collectAsStateWithLifecycle()
    val state = run ?: return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "正在转换 ${state.finishedCount + 1} / ${state.items.size}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        state.currentItem?.name ?: "准备中",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                }
                StatusPill(
                    text = "查看",
                    container = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { state.totalProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
            )
        }
    }
}
