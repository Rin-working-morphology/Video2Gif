package com.example.video2gif.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.video2gif.domain.CropAspect
import com.example.video2gif.domain.DitherMode
import com.example.video2gif.domain.FPS_OPTIONS
import com.example.video2gif.domain.MAX_BATCH_ITEMS
import com.example.video2gif.domain.OutputSpec
import com.example.video2gif.domain.QualityPreset
import com.example.video2gif.domain.SourceVideo
import com.example.video2gif.domain.VideoItemDraft
import com.example.video2gif.domain.formatDuration
import com.example.video2gif.domain.formatLoop
import com.example.video2gif.domain.formatResolution
import com.example.video2gif.domain.formatSpeed
import com.example.video2gif.domain.formatTimecode
import com.example.video2gif.domain.outputSpec
import com.example.video2gif.ui.AppViewModel
import com.example.video2gif.ui.Screen
import com.example.video2gif.ui.components.AppTopBar
import com.example.video2gif.ui.components.BottomGlassBar
import com.example.video2gif.ui.components.CropEditorSheet
import com.example.video2gif.ui.components.Hairline
import com.example.video2gif.ui.components.LocalReducedMotion
import com.example.video2gif.ui.components.LoopSheet
import com.example.video2gif.ui.components.MediaStage
import com.example.video2gif.ui.components.MetaText
import com.example.video2gif.ui.components.OptionSheet
import com.example.video2gif.ui.components.ParameterRow
import com.example.video2gif.ui.components.PrimaryAction
import com.example.video2gif.ui.components.SectionTitle
import com.example.video2gif.ui.components.SegmentOption
import com.example.video2gif.ui.components.SegmentedOptions
import com.example.video2gif.ui.components.SizeSheet
import com.example.video2gif.ui.components.SkeletonBox
import com.example.video2gif.ui.components.TrimTimeDialog
import com.example.video2gif.ui.components.TrimTimeline
import com.example.video2gif.ui.components.rememberReducedMotion
import com.example.video2gif.ui.components.rememberStartWithNotifications
import com.example.video2gif.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: AppViewModel) {
    val item = vm.activeItem
    val source = item?.source
    if (item == null || source == null) {
        MissingEditor(vm)
        return
    }
    val spec = item.recipe.outputSpec(source)
    val analysis = vm.analyses[item.id]
    val estimate = vm.estimateFor(item)
    val frames = vm.timelineStrips[item.id].orEmpty()
    val run by vm.run.collectAsStateWithLifecycle()
    val batchSize = vm.draft?.items?.size ?: 1
    var advancedOpen by rememberSaveable(item.id) { mutableStateOf(false) }
    var fpsOpen by remember { mutableStateOf(false) }
    var sizeOpen by remember { mutableStateOf(false) }
    var loopOpen by remember { mutableStateOf(false) }
    var colorsOpen by remember { mutableStateOf(false) }
    var ditherOpen by remember { mutableStateOf(false) }
    var trimOpen by remember { mutableStateOf(false) }
    val startWithNotifications = rememberStartWithNotifications { vm.startConversion() }
    val reducedMotion = rememberReducedMotion()

    val remaining = (MAX_BATCH_ITEMS - (vm.draft?.items?.size ?: 0)).coerceAtLeast(1)
    val pickMultiple = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(remaining.coerceAtLeast(2)),
    ) { uris -> if (uris.isNotEmpty()) vm.importVideos(uris) }
    val pickSingle = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) vm.importVideos(listOf(uri)) }
    val openPicker = {
        if (remaining == 1) {
            pickSingle.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        } else {
            pickMultiple.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
            )
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.stopPlayback() }
    DisposableEffect(Unit) {
        onDispose { vm.stopPlayback() }
    }

    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // 宽窗口（平板或横屏）切为左侧预览、右侧参数；预览高度服从可用高度。
            val wide = maxWidth >= 720.dp
            var barHeightPx by remember { mutableStateOf(0) }
            val bottomPadding = with(LocalDensity.current) { barHeightPx.toDp() } + 16.dp
            val stageMaxHeight = if (wide) {
                minOf(300.dp, maxHeight * 0.42f)
            } else {
                minOf(420.dp, maxHeight * 0.45f)
            }

            Column(Modifier.fillMaxSize()) {
                AppTopBar(
                    title = "调整 GIF",
                    subtitle = if (batchSize > 1) {
                        "第 ${vm.activeIndex + 1} / $batchSize 个 · ${item.name}"
                    } else {
                        item.name
                    },
                    onBack = { vm.back() },
                    actions = {
                        IconButton(
                            onClick = { vm.navigate(Screen.Settings) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = "设置",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                )
                if (wide) {
                    Row(Modifier.weight(1f)) {
                        Column(
                            Modifier
                                .weight(1.12f)
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = bottomPadding),
                        ) {
                            EditorMediaPane(
                                vm = vm,
                                item = item,
                                source = source,
                                spec = spec,
                                frames = frames,
                                batchSize = batchSize,
                                runRunning = run?.isRunning == true,
                                stageMaxHeight = stageMaxHeight,
                                onEditTimes = { trimOpen = true },
                                onAdd = openPicker,
                            )
                        }
                        VerticalDivider(
                            modifier = Modifier.fillMaxHeight(),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Column(
                            Modifier
                                .weight(0.88f)
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = bottomPadding),
                        ) {
                            EditorParametersPane(
                                vm = vm,
                                item = item,
                                spec = spec,
                                advancedOpen = advancedOpen,
                                onToggleAdvanced = { advancedOpen = !advancedOpen },
                                onOpenFps = { fpsOpen = true },
                                onOpenSize = { sizeOpen = true },
                                onOpenLoop = { loopOpen = true },
                                onOpenColors = { colorsOpen = true },
                                onOpenDither = { ditherOpen = true },
                                onOpenCrop = { vm.startCropEditing() },
                                modifier = Modifier.padding(horizontal = 16.dp),
                                reducedMotion = reducedMotion,
                            )
                        }
                    }
                } else {
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = bottomPadding),
                    ) {
                        EditorMediaPane(
                            vm = vm,
                            item = item,
                            source = source,
                            spec = spec,
                            frames = frames,
                            batchSize = batchSize,
                            runRunning = run?.isRunning == true,
                            stageMaxHeight = stageMaxHeight,
                            onEditTimes = { trimOpen = true },
                            onAdd = openPicker,
                        )
                        Spacer(Modifier.height(8.dp))
                        EditorParametersPane(
                            vm = vm,
                            item = item,
                            spec = spec,
                            advancedOpen = advancedOpen,
                            onToggleAdvanced = { advancedOpen = !advancedOpen },
                            onOpenFps = { fpsOpen = true },
                            onOpenSize = { sizeOpen = true },
                            onOpenLoop = { loopOpen = true },
                            onOpenColors = { colorsOpen = true },
                            onOpenDither = { ditherOpen = true },
                            onOpenCrop = { vm.startCropEditing() },
                            modifier = Modifier.padding(horizontal = 16.dp),
                            reducedMotion = reducedMotion,
                        )
                    }
                }
            }

            BottomGlassBar(
                Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { barHeightPx = it.height },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val analyzed = estimate?.analyzed == true && analysis?.analyzing != true
                        Text(
                            text = when {
                                estimate == null -> "正在准备"
                                analyzed -> "预计 ${estimate.label}"
                                else -> "初步预计 ${estimate.label}"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(2.dp))
                        MetaText(
                            buildString {
                                spec?.let {
                                    append(formatResolution(it.width, it.height))
                                    append(" · ")
                                    append("${it.frameCount} 帧")
                                    append(" · ")
                                }
                                append(
                                    formatDuration(
                                        ((item.recipe.trimEndMs - item.recipe.trimStartMs)
                                            / item.recipe.speed).toLong(),
                                    ),
                                )
                            },
                        )
                        if (analysis?.analyzing == true) {
                            Text(
                                "正在分析画面…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    if (run?.isRunning == true) {
                        PrimaryAction(
                            text = "查看进度",
                            onClick = { vm.openProgress() },
                        )
                    } else {
                        PrimaryAction(
                            text = if (batchSize > 1) "查看批次（$batchSize）" else "开始转换",
                            onClick = {
                                if (batchSize > 1) vm.navigate(Screen.Batch) else startWithNotifications()
                            },
                        )
                    }
                }
            }
        }

        if (fpsOpen) {
            OptionSheet(
                title = "每秒帧数",
                options = FPS_OPTIONS.map { it to "$it fps" },
                selected = item.recipe.fps,
                description = "源视频帧率较低时，提高设置不会增加细节；预计约 ${
                    spec?.frameCount ?: 0
                } 帧。",
                onSelect = {
                    vm.setFps(it)
                    fpsOpen = false
                },
                onDismiss = { fpsOpen = false },
            )
        }
        if (sizeOpen) {
            SizeSheet(
                selected = item.recipe.longSide,
                onSelect = {
                    vm.setLongSide(it)
                    sizeOpen = false
                },
                onDismiss = { sizeOpen = false },
            )
        }
        if (loopOpen) {
            LoopSheet(
                loop = item.recipe.loop,
                count = item.recipe.loopCount,
                onConfirm = { mode, count ->
                    vm.setLoopMode(mode, count)
                    loopOpen = false
                },
                onDismiss = { loopOpen = false },
            )
        }
        if (colorsOpen) {
            OptionSheet(
                title = "颜色数量",
                options = listOf(64, 128, 256).map { it to "$it 色" },
                selected = item.recipe.colorCount,
                description = "颜色越少体积越小，渐变与肤色更容易出现色带。",
                onSelect = {
                    vm.setColorCount(it)
                    colorsOpen = false
                },
                onDismiss = { colorsOpen = false },
            )
        }
        if (ditherOpen) {
            OptionSheet(
                title = "抖动",
                options = listOf(
                    DitherMode.Off to "关闭",
                    DitherMode.Light to "轻微",
                    DitherMode.Standard to "标准",
                ),
                selected = item.recipe.dither,
                description = "有序抖动可以让渐变更平滑，但可能增加体积。",
                onSelect = {
                    vm.setDither(it)
                    ditherOpen = false
                },
                onDismiss = { ditherOpen = false },
            )
        }
        if (trimOpen) {
            TrimTimeDialog(
                startMs = item.recipe.trimStartMs,
                endMs = item.recipe.trimEndMs,
                durationMs = source.durationMs,
                playheadMs = vm.playheadMs,
                onConfirm = { start, end ->
                    vm.setTrim(start, end)
                    trimOpen = false
                },
                onDismiss = { trimOpen = false },
            )
        }
        if (vm.cropEditing) {
            CropEditorSheet(
                frame = vm.cropSourceFrame,
                loading = vm.cropSourceLoading,
                source = source,
                recipe = item.recipe,
                onRotate = vm::setRotation,
                onApply = { vm.setCrop(it) },
                onDismiss = { vm.cancelCropEditing() },
            )
        }
    }
}

@Composable
private fun EditorMediaPane(
    vm: AppViewModel,
    item: VideoItemDraft,
    source: SourceVideo,
    spec: OutputSpec?,
    frames: List<android.graphics.Bitmap>,
    batchSize: Int,
    runRunning: Boolean,
    stageMaxHeight: Dp,
    onEditTimes: () -> Unit,
    onAdd: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        MediaStage(
            spec = spec,
            frame = vm.preview,
            loading = vm.previewLoading,
            error = vm.previewError,
            showOriginal = vm.showOriginal,
            isPlaying = vm.isPlaying,
            timecode = formatTimecode(vm.playheadMs),
            onToggleOriginal = vm::toggleOriginal,
            onTogglePlay = vm::togglePlayback,
            modifier = Modifier.padding(vertical = 8.dp),
            maxStageHeight = stageMaxHeight,
        )
        TrimTimeline(
            frames = frames,
            durationMs = source.durationMs,
            startMs = item.recipe.trimStartMs,
            endMs = item.recipe.trimEndMs,
            playheadMs = vm.playheadMs,
            onSeek = vm::seekTo,
            onTrimChange = { start, end -> vm.setTrim(start, end) },
            onEditTimes = onEditTimes,
        )
        Text(
            "点按起点或终点可精确输入时间",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        )
        if (batchSize > 1) {
            Spacer(Modifier.height(12.dp))
            BatchRail(vm = vm, onAdd = onAdd)
            Spacer(Modifier.height(12.dp))
            BatchNote()
        }
        if (runRunning) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    "已有转换任务进行中，当前调整不会影响已开始的任务。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun BatchNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Info,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "修改仅作用于当前视频；统一参数在批次页设置。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EditorParametersPane(
    vm: AppViewModel,
    item: VideoItemDraft,
    spec: OutputSpec?,
    advancedOpen: Boolean,
    onToggleAdvanced: () -> Unit,
    onOpenFps: () -> Unit,
    onOpenSize: () -> Unit,
    onOpenLoop: () -> Unit,
    onOpenColors: () -> Unit,
    onOpenDither: () -> Unit,
    onOpenCrop: () -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
) {
    Column(modifier) {
        Spacer(Modifier.height(16.dp))
        val preset = QualityPreset.from(item.recipe.colorCount, item.recipe.dither)
        SectionTitle(
            "画质",
            trailing = {
                if (preset == null) {
                    Text(
                        "自定义",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        SegmentedOptions(
            options = listOf(
                SegmentOption(QualityPreset.Compact, "省空间"),
                SegmentOption(QualityPreset.Balanced, "均衡"),
                SegmentOption(QualityPreset.Fine, "更细腻"),
            ),
            selected = preset,
            onSelect = vm::setQuality,
        )
        Text(
            "${item.recipe.colorCount} 色 · ${ditherLabel(item.recipe.dither)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )

        Spacer(Modifier.height(24.dp))
        SectionTitle("输出")
        Spacer(Modifier.height(4.dp))
        ParameterRow(
            title = "每秒帧数",
            value = "${item.recipe.fps} fps",
            supporting = spec?.let { "约 ${it.frameCount} 帧" },
            onClick = onOpenFps,
        )
        Hairline()
        ParameterRow(
            title = "尺寸",
            value = "长边 ${item.recipe.longSide}",
            supporting = spec?.let { formatResolution(it.width, it.height) },
            onClick = onOpenSize,
        )
        Hairline()
        ParameterRow(
            title = "裁剪与旋转",
            value = cropLabel(item),
            supporting = "调整取景框与画面方向",
            onClick = onOpenCrop,
        )

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onToggleAdvanced)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "高级设置",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            val rotation by animateFloatAsState(
                targetValue = if (advancedOpen) 180f else 0f,
                animationSpec = tween(if (reducedMotion) 0 else 180),
                label = "advancedChevron",
            )
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = if (advancedOpen) "收起高级设置" else "展开高级设置",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = advancedOpen,
            enter = fadeIn(tween(if (reducedMotion) 0 else 180)) +
                expandVertically(tween(if (reducedMotion) 0 else 200)),
            exit = fadeOut(tween(if (reducedMotion) 0 else 140)) +
                shrinkVertically(tween(if (reducedMotion) 0 else 180)),
        ) {
            Column {
                Text(
                    "播放速度",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                SegmentedOptions(
                    options = listOf(0.5f, 1f, 1.5f, 2f).map {
                        SegmentOption(it, formatSpeed(it))
                    },
                    selected = item.recipe.speed,
                    onSelect = vm::setSpeed,
                )
                Text(
                    "改变输出时长与采样时刻，不影响音轨（GIF 无声音）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
                Spacer(Modifier.height(8.dp))
                ParameterRow(
                    title = "循环",
                    value = formatLoop(item.recipe.loop, item.recipe.loopCount),
                    supporting = "部分播放器会忽略有限循环",
                    onClick = onOpenLoop,
                )
                Hairline()
                ParameterRow(
                    title = "颜色数量",
                    value = "${item.recipe.colorCount} 色",
                    supporting = "GIF 全局调色板容量",
                    onClick = onOpenColors,
                )
                Hairline()
                ParameterRow(
                    title = "抖动",
                    value = ditherLabel(item.recipe.dither),
                    supporting = "有序抖动，结果稳定可复现",
                    onClick = onOpenDither,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BatchRail(vm: AppViewModel, onAdd: () -> Unit) {
    val items = vm.draft?.items.orEmpty()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            val selected = index == vm.activeIndex
            val border = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier = Modifier
                    .size(width = 76.dp, height = 52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { vm.selectItemAt(index) }
                    .semantics {
                        contentDescription = "第 ${index + 1} 个视频 ${item.name}"
                    },
            ) {
                val bitmap = vm.thumbnails[item.id]
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    SkeletonBox(Modifier.fillMaxSize(), RoundedCornerShape(10.dp))
                }
                if (item.error != null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)),
                    )
                }
            }
        }
        item {
            Box(
                modifier = Modifier
                    .size(width = 60.dp, height = 52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable(onClick = onAdd)
                    .semantics { contentDescription = "添加视频" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun ditherLabel(mode: DitherMode): String = when (mode) {
    DitherMode.Off -> "无抖动"
    DitherMode.Light -> "轻微抖动"
    DitherMode.Standard -> "标准抖动"
}

private fun cropLabel(item: VideoItemDraft): String {
    val label = when (item.recipe.crop.aspect) {
        CropAspect.Original -> "原比例"
        CropAspect.Square -> "1:1"
        CropAspect.FourThree -> "4:3"
        CropAspect.NineSixteen -> "9:16"
        CropAspect.Free -> "自由"
    }
    return if (item.recipe.userRotation == 0) label
    else "$label · 旋转 ${item.recipe.userRotation}°"
}

@Composable
private fun MissingEditor(vm: AppViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "没有可编辑的视频",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "返回首页重新选择，或从批次中移除无法读取的项。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = { vm.back() }) { Text("返回") }
    }
}
