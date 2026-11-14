package com.example.video2gif.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.video2gif.ui.theme.AppTheme
import com.example.video2gif.ui.theme.Motion
import com.example.video2gif.ui.theme.PanelShape

val LocalReducedMotion = compositionLocalOf { false }

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
        scale == 0f
    }
}

/**
 * 顶栏。背景保持透明，让极光从页面顶部连续地穿过去；
 * 标题自身有足够对比度，不需要再垫一块实色。
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                Spacer(Modifier.width(16.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onBack == null) 0.dp else 4.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

/** 顶栏里的玻璃图标按钮：48dp 触控区内放 40dp 的圆形玻璃。 */
@Composable
fun IconGlassButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(AppTheme.extended.glassFill)
                .border(1.dp, AppTheme.extended.glassBorder, CircleShape)
                .clickable(
                    role = Role.Button,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 24.dp),
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 玻璃面板：半透明底色 + 顶部高光 + 1dp 边缘 + 内侧薄荷染色。
 * [ambient] 打开时在面板后方加一层柔光，用于主卡片；普通分组不开。
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = PanelShape.panel,
    ambient: Boolean = false,
    content: @Composable () -> Unit,
) {
    val extended = AppTheme.extended
    Box(modifier) {
        if (ambient) {
            HaloGlow(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 12.dp, vertical = 18.dp),
                strength = 0.24f,
            )
        }
        Box(Modifier.matchParentSize().clip(shape).background(extended.glassFill)) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to extended.glassTint,
                            0.42f to Color.Transparent,
                            0.68f to extended.auroraCore.copy(alpha = 0.05f),
                            1f to extended.auroraCore.copy(alpha = 0.16f),
                        ),
                    ),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to extended.glassHighlight.copy(alpha = 0.10f),
                            0.40f to Color.Transparent,
                        ),
                    ),
            )
            // 内侧 1dp 边缘：用渐变描边代替纯色描边，顶部更亮，底部隐去。
            Box(
                Modifier
                    .matchParentSize()
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                extended.glassBorder,
                                extended.glassBorder.copy(alpha = extended.glassBorder.alpha * 0.45f),
                            ),
                        ),
                        shape = shape,
                    ),
            )
            // 顶边一道 1dp 高光，模拟玻璃边缘的折射。
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(0.86f)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                extended.glassHighlight.copy(alpha = 0.55f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }
        content()
    }
}

@Composable
fun BottomGlassBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val extended = AppTheme.extended
    val background = MaterialTheme.colorScheme.background
    val shape = PanelShape.sheet
    Column(modifier.fillMaxWidth()) {
        // 内容滚到操作区下方时用短渐变收尾，避免内容被硬边裁断。
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(background.copy(alpha = 0f), background),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(extended.glassFill.copy(alpha = 0.985f)),
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to extended.glassTint,
                            0.45f to Color.Transparent,
                        ),
                    ),
            )
            // 顶边是一条薄荷到透明的极细光线，代替通用分隔线。
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                extended.glow.copy(alpha = 0.45f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                content()
            }
        }
    }
}

/**
 * 主操作：薄荷渐变胶囊。按下时轻微缩放，松开回弹；不使用投影。
 */
@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "primaryScale",
    )
    val scheme = MaterialTheme.colorScheme
    val reducedMotion = LocalReducedMotion.current
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .graphicsLayer {
                scaleX = if (reducedMotion) 1f else scale
                scaleY = if (reducedMotion) 1f else scale
                alpha = if (enabled) 1f else 0.42f
            }
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        scheme.primary,
                        lerpColor(scheme.primary, scheme.tertiary, 0.55f),
                    ),
                ),
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = ripple(color = scheme.onPrimary),
                onClick = onClick,
            )
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = scheme.onPrimary,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun SecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "secondaryScale",
    )
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.42f
            }
            .clip(CircleShape)
            .background(scheme.surfaceContainerLow.copy(alpha = 0.65f))
            .border(1.dp, scheme.outlineVariant, CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = ripple(color = scheme.primary),
                onClick = onClick,
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = scheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurface,
            maxLines = 1,
        )
    }
}

@Immutable
data class SegmentOption<T>(val value: T, val label: String)

@Composable
fun <T> SegmentedOptions(
    options: List<SegmentOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = PanelShape.card
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainerLow.copy(alpha = 0.55f))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.7f), shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option.value == selected
            val background by animateColorAsState(
                targetValue = if (isSelected) scheme.primary else Color.Transparent,
                animationSpec = tween(Motion.Fast, easing = Motion.EaseOutQuart),
                label = "segmentBackground",
            )
            val foreground by animateColorAsState(
                targetValue = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant,
                animationSpec = tween(Motion.Fast, easing = Motion.EaseOutQuart),
                label = "segmentForeground",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(if (compact) 40.dp else 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(background)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(option.value) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = foreground,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun ParameterRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        role = Role.Button,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = scheme.primary),
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
            )
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFeatureSettings = "tnum",
                fontWeight = FontWeight.Medium,
            ),
            color = scheme.primary,
        )
        if (onClick != null && showChevron) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 2.dp)
                    .size(18.dp),
            )
        }
    }
}

/** 分组标题前的小菱形：取代彩色侧边条，也是画面里重复出现的几何母题。 */
@Composable
fun DiamondMark(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    markSize: Dp = 5.dp,
) {
    Canvas(modifier.size(markSize)) {
        rotate(degrees = 45f) {
            drawRect(color = color, topLeft = Offset.Zero, size = size)
        }
    }
}

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DiamondMark(Modifier.padding(end = 8.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.outlineVariant,
                        Color.Transparent,
                    ),
                ),
            ),
    )
}

@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    icon: ImageVector? = null,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = container,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

/** 骨架块：一道缓慢扫过的高光，代替转圈等待。 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    val extended = AppTheme.extended
    val reducedMotion = LocalReducedMotion.current
    val sweep = if (reducedMotion) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, easing = androidx.compose.animation.core.LinearEasing),
            ),
            label = "sweep",
        ).value
    }
    Box(modifier.clip(shape).background(extended.skeleton)) {
        Canvas(Modifier.matchParentSize()) {
            val width = size.width
            val start = -width * 0.6f + sweep * width * 1.8f
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        extended.skeletonHighlight.copy(alpha = 0.9f),
                        Color.Transparent,
                    ),
                    start = Offset(start, 0f),
                    end = Offset(start + width * 0.6f, size.height),
                ),
            )
        }
    }
}

@Composable
fun MetaText(
    text: String,
    modifier: Modifier = Modifier,
    tabular: Boolean = true,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall.let {
            if (tabular) it.copy(fontFeatureSettings = "tnum") else it
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun TimecodeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFeatureSettings = "tnum",
            fontWeight = FontWeight.Medium,
        ),
        color = color,
    )
}

/**
 * 开始转换前按需请求通知权限；拒绝通知不影响任务本身，应用内仍完整显示状态。
 */
@Composable
fun rememberStartWithNotifications(onStart: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onStart)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        latest()
    }
    return {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            latest()
        }
    }
}

fun shareGif(context: Context, uriString: String, title: String = "分享 GIF") {
    val uri = Uri.parse(uriString)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/gif"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title))
}

fun shareGifs(context: Context, uriStrings: List<String>, title: String = "分享 GIF") {
    if (uriStrings.isEmpty()) return
    val uris = uriStrings.map { Uri.parse(it) }
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/gif"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title))
}

/** 视频舞台里的留白或没有帧时使用的占位。 */
@Composable
fun StagePlaceholder(modifier: Modifier = Modifier, text: String) {
    val extended = AppTheme.extended
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = extended.glassTextMuted,
        )
    }
}

/** 颜色线性插值，供渐变端点使用（避免每处都 import lerp）。 */
internal fun lerpColor(start: Color, stop: Color, fraction: Float): Color =
    androidx.compose.ui.graphics.lerp(start, stop, fraction)
