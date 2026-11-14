package com.example.video2gif.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.video2gif.domain.MAX_BATCH_ITEMS
import com.example.video2gif.ui.AppViewModel
import com.example.video2gif.ui.Screen
import com.example.video2gif.ui.components.AuroraBackdrop
import com.example.video2gif.ui.components.GlassPanel
import com.example.video2gif.ui.components.IconGlassButton
import com.example.video2gif.ui.components.OrbitOverlay
import com.example.video2gif.ui.components.OrnamentField
import com.example.video2gif.ui.components.PrimaryAction
import com.example.video2gif.ui.components.SkeletonBox
import com.example.video2gif.ui.components.Sparkle
import com.example.video2gif.ui.components.entrance
import com.example.video2gif.ui.components.pressFeedback
import com.example.video2gif.ui.theme.AppTheme
import com.example.video2gif.ui.theme.Motion
import com.example.video2gif.ui.theme.PanelShape

/**
 * 首页。参考图的构图直接成为页面结构：
 * 中央一张竖向玻璃卡（这里是选择视频的入口），四周是极光、胶片条、轨道线与星芒。
 * 卡片始终用于选择新视频。
 */
@Composable
fun HomeScreen(vm: AppViewModel) {
    val pickMultiple = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_BATCH_ITEMS),
    ) { uris ->
        vm.importVideos(uris, startNewBatch = true)
    }
    val launchPicker = {
        pickMultiple.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        AuroraBackdrop(Modifier.fillMaxSize())
        val fontScale = LocalDensity.current.fontScale
        val scrollContent = maxHeight < 600.dp || fontScale > 1.3f

        Column(
            Modifier
                .fillMaxSize()
                .then(if (scrollContent) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HomeHeader(onSettings = { vm.navigate(Screen.Settings) })

            // 主视觉区：卡片占可用高度的 62%–78%，其余留给装饰与星芒。
            BoxWithConstraints(
                Modifier
                    .then(
                        if (scrollContent) Modifier.height(400.dp + 100.dp * (fontScale - 1f).coerceAtLeast(0f))
                        else Modifier.weight(1f),
                    )
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .entrance(),
            ) {
                // 大字号下卡片加宽加高，避免按钮与提示拥挤。
                val cardWidth: Dp = (maxWidth * 0.66f * (1f + (fontScale - 1f) * 0.35f))
                    .coerceIn(minOf(216.dp, maxWidth - 32.dp), minOf(360.dp, maxWidth - 32.dp))
                val cardHeight: Dp = (maxHeight * 0.68f * (1f + (fontScale - 1f) * 0.35f))
                    .coerceIn(minOf(320.dp, maxHeight), minOf(500.dp, maxHeight))

                OrnamentField(Modifier.fillMaxSize())
                // 轨道在主卡片后方穿行，避免划过标题、预览与按钮。
                OrbitOverlay(Modifier.fillMaxSize(), alpha = 0.22f)

                EmptyHeroCard(
                    width = cardWidth,
                    height = cardHeight,
                    importing = vm.isImporting,
                    onClick = launchPicker,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            HomeStatusSection(vm = vm)

            // 底部落在明亮的极光带上，文字放进一枚低饱和小胶囊里保证对比度。
            Box(
                Modifier
                    .navigationBarsPadding()
                    .padding(top = 10.dp, bottom = 14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AppTheme.extended.glassFill.copy(alpha = 0.86f))
                        .border(1.dp, AppTheme.extended.glassBorder, CircleShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "每批最多 $MAX_BATCH_ITEMS 个 · 全部在设备上处理",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .padding(end = 12.dp, top = 14.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        IconGlassButton(
            icon = Icons.Rounded.Settings,
            contentDescription = "设置",
            onClick = onSettings,
        )
    }
}

/**
 * 空白态的玻璃卡。内部留白与星芒标记直接来自参考图；
 * 主操作是卡片底部的一枚薄荷胶囊，整张卡本身也可点击。
 */
@Composable
private fun EmptyHeroCard(
    width: Dp,
    height: Dp,
    importing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = AppTheme.extended
    val interaction = remember { MutableInteractionSource() }
    GlassPanel(
        modifier = modifier
            .width(width)
            .height(height)
            .pressFeedback(interaction, enabled = !importing, pressedScale = 0.99f)
            .clip(PanelShape.panel)
            .clickable(
                enabled = !importing,
                role = Role.Button,
                interactionSource = interaction,
                indication = ripple(color = MaterialTheme.colorScheme.primary),
                onClick = onClick,
            )
            .semantics {
                contentDescription = if (importing) "正在读取视频信息" else "选择视频，先预览效果，确认后再转换"
            },
        ambient = true,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Sparkle(sizeDp = 26.dp, color = extended.sparkle, alpha = 0.95f)
            Spacer(Modifier.height(10.dp))
            DottedLine()
            Spacer(Modifier.weight(1f))
            if (importing) {
                ShimmeringFrames()
                Spacer(Modifier.height(18.dp))
                Text(
                    "正在读取视频信息…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ApertureMark()
                Spacer(Modifier.height(18.dp))
            }
            Spacer(Modifier.weight(1f))
            PrimaryAction(
                text = if (importing) "读取中" else "选择视频",
                icon = if (importing) null else Icons.Rounded.Add,
                onClick = onClick,
                enabled = !importing,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 竖向点线。参考图卡片顶部的标记，纯装饰。 */
@Composable
private fun DottedLine(dots: Int = 3) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(dots) {
            Box(
                Modifier
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.55f)),
            )
        }
    }
}

/** 光圈 + 播放三角：卡片中央的视觉焦点，也暗示“视频”。 */
@Composable
private fun ApertureMark(size: Dp = 96.dp) {
    val extended = AppTheme.extended
    val primary = MaterialTheme.colorScheme.primary
    val ornament = extended.ornament
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2f
            drawCircle(
                color = ornament.copy(alpha = 0.24f),
                radius = radius - 1.dp.toPx(),
                style = Stroke(width = 1.dp.toPx()),
            )
            drawCircle(
                color = ornament.copy(alpha = 0.12f),
                radius = radius * 0.72f,
                style = Stroke(width = 1.dp.toPx()),
            )
            // 四段弧线组成断续的光环
            val arcRadius = radius * 0.86f
            for (index in 0 until 4) {
                val start = index * 90f + 12f
                drawArc(
                    color = primary.copy(alpha = 0.75f),
                    startAngle = start,
                    sweepAngle = 46f,
                    useCenter = false,
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                    size = androidx.compose.ui.geometry.Size(arcRadius * 2, arcRadius * 2),
                    style = Stroke(width = 1.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
            val triangle = Path().apply {
                val s = radius * 0.30f
                moveTo(center.x - s * 0.55f, center.y - s)
                lineTo(center.x + s * 0.95f, center.y)
                lineTo(center.x - s * 0.55f, center.y + s)
                close()
            }
            drawPath(triangle, primary.copy(alpha = 0.95f))
        }
    }
}

@Composable
private fun ShimmeringFrames() {
    Box(Modifier.size(width = 120.dp, height = 84.dp), contentAlignment = Alignment.Center) {
        SkeletonBox(
            Modifier
                .size(width = 92.dp, height = 64.dp)
                .padding(top = 8.dp),
            PanelShape.thumb,
        )
        SkeletonBox(
            Modifier
                .size(width = 104.dp, height = 70.dp)
                .align(Alignment.Center),
            PanelShape.thumb,
        )
    }
}

/** 卡片下方的状态区：最近一次转换。 */
@Composable
private fun HomeStatusSection(
    vm: AppViewModel,
) {
    val run by vm.run.collectAsStateWithLifecycle()
    val current = run
    val gap = 6.dp
    Column(
        Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        when {
            current?.isRunning == true -> {
                Spacer(Modifier.height(gap))
                RunStrip(
                    title = "正在转换 ${current.finishedCount + 1} / ${current.items.size}",
                    subtitle = current.currentItem?.name ?: "准备中",
                    progress = current.totalProgress,
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = { vm.openProgress() },
                )
            }
            current != null && current.hasRetryable -> {
                Spacer(Modifier.height(gap))
                RunStrip(
                    title = "上次转换未完成",
                    subtitle = "完成 ${current.succeededCount} 个 · " +
                        "${current.failedCount + current.interruptedCount + current.canceledCount} 个可重试",
                    progress = null,
                    accent = AppTheme.extended.warning,
                    onClick = { vm.openProgress() },
                    icon = Icons.Rounded.WarningAmber,
                )
            }
            current != null && current.succeededCount > 0 -> {
                Spacer(Modifier.height(gap))
                RunStrip(
                    title = if (current.failedCount > 0) {
                        "完成 ${current.succeededCount} 个，失败 ${current.failedCount} 个"
                    } else {
                        "上次完成 ${current.succeededCount} 个 GIF"
                    },
                    subtitle = "查看结果与分享",
                    progress = null,
                    accent = AppTheme.extended.success,
                    onClick = { vm.openResult() },
                    icon = Icons.Rounded.CheckCircle,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun RunStrip(
    title: String,
    subtitle: String,
    progress: Float?,
    accent: Color,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val animated by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = tween(Motion.Base, easing = Motion.EaseOutQuart),
        label = "runStrip",
    )
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PanelShape.card)
            .clickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = accent),
                onClick = onClick,
            ),
        shape = PanelShape.card,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                } else {
                    Canvas(Modifier.size(18.dp)) {
                        drawArc(
                            color = accent,
                            startAngle = -90f,
                            sweepAngle = 360f * animated,
                            useCenter = false,
                            style = Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Icon(
                Icons.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
