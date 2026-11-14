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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.video2gif.domain.FPS_OPTIONS
import com.example.video2gif.domain.LONG_SIDE_OPTIONS
import com.example.video2gif.domain.LoopMode
import com.example.video2gif.domain.MAX_BATCH_ITEMS
import com.example.video2gif.domain.QualityPreset
import com.example.video2gif.domain.VideoItemDraft
import com.example.video2gif.domain.formatBytes
import com.example.video2gif.domain.formatDuration
import com.example.video2gif.domain.formatResolution
import com.example.video2gif.domain.formatSpeed
import com.example.video2gif.domain.outputSpec
import com.example.video2gif.ui.AppViewModel
import com.example.video2gif.ui.Screen
import com.example.video2gif.ui.components.AppTopBar
import com.example.video2gif.ui.components.AuroraBackdrop
import com.example.video2gif.ui.components.GlassPanel
import com.example.video2gif.ui.theme.PanelShape
import com.example.video2gif.ui.components.BottomGlassBar
import com.example.video2gif.ui.components.Hairline
import com.example.video2gif.ui.components.MetaText
import com.example.video2gif.ui.components.PrimaryAction
import com.example.video2gif.ui.components.SkeletonBox
import com.example.video2gif.ui.components.StatusPill
import com.example.video2gif.ui.components.rememberStartWithNotifications
import kotlin.math.roundToLong

@Composable
fun BatchScreen(vm: AppViewModel) {
    val batch = vm.draft ?: return
    val run by vm.run.collectAsStateWithLifecycle()
    var unifiedOpen by remember { mutableStateOf(false) }
    val startWithNotifications = rememberStartWithNotifications { vm.startConversion() }
    var barHeightPx by remember { mutableStateOf(0) }
    val bottomPadding = with(LocalDensity.current) { barHeightPx.toDp() } + 12.dp

    val remaining = (MAX_BATCH_ITEMS - batch.items.size).coerceAtLeast(1)
    val pickMultiple = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(remaining.coerceAtLeast(2)),
    ) { uris -> if (uris.isNotEmpty()) vm.importVideos(uris) }
    val pickSingle = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) vm.importVideos(listOf(uri)) }
    val launchPicker = {
        if (batch.items.size >= MAX_BATCH_ITEMS) {
            vm.showNotice("每批最多 $MAX_BATCH_ITEMS 个视频")
        } else if (remaining == 1) {
            pickSingle.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        } else {
            pickMultiple.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
            )
        }
    }

    val valid = batch.items.filter { it.source != null && it.error == null }
    val invalidCount = batch.items.size - valid.size
    val analyzed = valid.mapNotNull { item ->
        vm.freshAnalysis(item.id)?.estimate?.takeIf { it.analyzed }
    }
    val allAnalyzed = analyzed.size == valid.size && valid.isNotEmpty()
    val totalLow = analyzed.sumOf { it.lowBytes }
    val totalHigh = analyzed.sumOf { it.highBytes }
    val totalDuration = valid.sumOf { item ->
        ((item.recipe.trimEndMs - item.recipe.trimStartMs) / item.recipe.speed).roundToLong()
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackdrop(Modifier.fillMaxSize(), intensity = 0.9f)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            AppTopBar(
                title = "批次",
                subtitle = "${batch.items.size} / $MAX_BATCH_ITEMS 个视频",
                onBack = { vm.back() },
                actions = {
                    TextButton(onClick = launchPicker, enabled = batch.items.size < MAX_BATCH_ITEMS) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加")
                    }
                },
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = bottomPadding),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "全部视频",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "点击一行回到该视频调整",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { unifiedOpen = true }) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("统一参数")
                    }
                }

                Spacer(Modifier.height(8.dp))
                GlassPanel(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    shape = PanelShape.card,
                ) {
                  Column {
                    batch.items.forEachIndexed { index, item ->
                        BatchRow(
                            vm = vm,
                            item = item,
                            index = index,
                            onOpen = {
                                if (item.error != null) {
                                    vm.showNotice("请先移除无法读取的视频")
                                } else {
                                    vm.selectItemAt(index)
                                    vm.navigate(Screen.Editor)
                                }
                            },
                            onRemove = { vm.removeItem(item.id) },
                        )
                        if (index != batch.items.lastIndex) {
                            Hairline(Modifier.padding(start = 88.dp))
                        }
                    }
                  }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MetaText("总输出时长 ${formatDuration(totalDuration)}")
                    MetaText(
                        if (allAnalyzed) "已分析 ${valid.size} 个视频"
                        else "已估算 ${analyzed.size} / ${valid.size}",
                    )
                }
                if (!allAnalyzed && valid.isNotEmpty()) {
                    Text(
                        "正在分析画面，合计估算会在全部就绪后显示。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
                if (invalidCount > 0) {
                    Text(
                        "有 $invalidCount 个视频无法读取，需要移除后才能开始整批转换。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                if (batch.items.size < MAX_BATCH_ITEMS) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "还可添加 ${MAX_BATCH_ITEMS - batch.items.size} 个视频，统一参数会写入同一批",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = launchPicker) { Text("添加") }
                    }
                }
            }
        }

        BottomGlassBar(Modifier.align(Alignment.BottomCenter).onSizeChanged { barHeightPx = it.height }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            invalidCount > 0 -> "需要先处理 $invalidCount 个视频"
                            run?.isRunning == true -> "转换正在进行"
                            allAnalyzed -> "合计预计 ${formatBytes(totalLow)} – ${formatBytes(totalHigh)}"
                            else -> "正在估算合计"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(2.dp))
                    MetaText(
                        "${valid.size} 个可转换 · 总时长 ${formatDuration(totalDuration)}",
                    )
                }
                Spacer(Modifier.width(12.dp))
                if (run?.isRunning == true) {
                    PrimaryAction("查看进度", onClick = { vm.openProgress() })
                } else {
                    PrimaryAction(
                        text = "开始转换（${valid.size}）",
                        onClick = startWithNotifications,
                        enabled = invalidCount == 0 && valid.isNotEmpty(),
                    )
                }
            }
        }
    }

    if (unifiedOpen) {
        UnifiedParamsSheet(
            vm = vm,
            onDismiss = { unifiedOpen = false },
        )
    }
}

@Composable
private fun BatchRow(
    vm: AppViewModel,
    item: VideoItemDraft,
    index: Int,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val thumb = vm.thumbnails[item.id]
    val spec = item.recipe.outputSpec(item.source)
    val estimate = vm.estimateFor(item)
    val analyzing = vm.analyses[item.id]?.analyzing == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 46.dp)
                .clip(PanelShape.thumb)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), PanelShape.thumb),
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                SkeletonBox(Modifier.fillMaxSize(), PanelShape.thumb)
            }
            if (item.error != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.82f)),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (item.error == null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        estimate?.label ?: "—",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFeatureSettings = "tnum",
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.error != null) {
                    Text(
                        item.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    MetaText(
                        text = buildString {
                            spec?.let {
                                append(formatResolution(it.width, it.height))
                                append(" · ")
                                append("${item.recipe.fps} fps · ")
                            }
                            append(
                                formatDuration(
                                    ((item.recipe.trimEndMs - item.recipe.trimStartMs) /
                                        item.recipe.speed).roundToLong(),
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (analyzing) {
                        Text(
                            "分析中",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (item.hasIndividualSettings) {
                        StatusPill(
                            text = "单独设置",
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(44.dp).semantics {
                contentDescription = "移除 ${item.name}"
            },
        ) {
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun UnifiedParamsSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val batch = vm.draft ?: return
    val defaults = batch.defaults
    var quality by remember { mutableStateOf(QualityPreset.from(defaults.colorCount, defaults.dither) ?: QualityPreset.Balanced) }
    var fps by remember { mutableStateOf(defaults.fps) }
    var longSide by remember { mutableStateOf(defaults.longSide) }
    var speed by remember { mutableStateOf(defaults.speed) }
    var loop by remember { mutableStateOf(defaults.loop) }
    var overwrite by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text("统一参数", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "以下 5 项将写入 ${batch.items.size} 个视频。截取时间与取景框始终单独保留。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            Label("画质")
            ChipRow(
                options = listOf(
                    QualityPreset.Compact to "省空间",
                    QualityPreset.Balanced to "均衡",
                    QualityPreset.Fine to "更细腻",
                ),
                selected = quality,
                onSelect = { quality = it },
            )
            Spacer(Modifier.height(14.dp))
            Label("每秒帧数")
            ChipRow(
                options = FPS_OPTIONS.map { it to "$it" },
                selected = fps,
                onSelect = { fps = it },
            )
            Spacer(Modifier.height(14.dp))
            Label("尺寸（长边）")
            ChipRow(
                options = LONG_SIDE_OPTIONS.map { it to "$it" },
                selected = longSide,
                onSelect = { longSide = it },
            )
            Spacer(Modifier.height(14.dp))
            Label("速度")
            ChipRow(
                options = listOf(0.5f, 1f, 1.5f, 2f).map { it to formatSpeed(it) },
                selected = speed,
                onSelect = { speed = it },
            )
            Spacer(Modifier.height(14.dp))
            Label("循环")
            ChipRow(
                options = listOf(
                    LoopMode.Infinite to "无限",
                    LoopMode.Once to "一次",
                    LoopMode.Custom to "自定义",
                ),
                selected = loop,
                onSelect = { loop = it },
            )

            Spacer(Modifier.height(18.dp))
            Hairline()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("覆盖已有单独设置", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (overwrite) {
                            "所有视频的对应字段都会被改写。"
                        } else {
                            "只写入没有单独设置过的字段。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = overwrite, onCheckedChange = { overwrite = it })
            }
            Hairline()
            Spacer(Modifier.height(12.dp))
            PrimaryAction(
                text = "应用到全部",
                onClick = {
                    vm.applyUnifiedParameters(quality, fps, longSide, speed, loop, overwrite)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Transparent,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = value == selected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}
