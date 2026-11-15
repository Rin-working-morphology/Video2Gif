package com.example.video2gif.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.video2gif.ui.theme.AppTheme
import com.example.video2gif.ui.theme.Motion
import com.example.video2gif.R
import kotlin.math.min

/*
 * 氛围层：水彩色块 + 几何装饰。
 *
 * 规则：
 * - 只做背景与装饰，从不承载文字或接收触摸。
 * - 极光用一次性绘制的静态 Canvas，靠 graphicsLayer 做慢速漂移，避免每帧重绘背景。
 * - 系统关闭动画时，所有循环动画停在固定相位，装饰仍然可见。
 */

/** 水彩背景色块。上下使用独立透明 PNG，几何装饰由 OrnamentField 继续绘制。 */
@Composable
fun AuroraBackdrop(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    showEdgeOrnaments: Boolean = true,
) {
    val background = MaterialTheme.colorScheme.background
    val reducedMotion = LocalReducedMotion.current

    val phase = if (reducedMotion) {
        null
    } else {
        val transition = rememberInfiniteTransition(label = "auroraDrift")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(Motion.Aurora, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "drift",
        )
    }

    Box(modifier.background(background)) {
        Image(
            painter = painterResource(R.drawable.watercolor_top),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    val drift = phase?.value ?: 0.5f
                    alpha = intensity
                    scaleX = 1.06f + drift * 0.02f
                    translationX = -size.width * 0.006f * drift
                },
        )
        Image(
            painter = painterResource(R.drawable.watercolor_bottom),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.BottomCenter,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    val drift = phase?.value ?: 0.5f
                    alpha = intensity
                    scaleX = 1.06f + drift * 0.02f
                    translationX = size.width * 0.006f * drift
                },
        )
        if (showEdgeOrnaments) {
            EdgeOrnaments(Modifier.matchParentSize(), intensity)
        }
    }
}

/** 内容页用宽弧暗纹呼应上下星环，避免竖条被误认为滚动条。 */
@Composable
private fun EdgeOrnaments(modifier: Modifier, intensity: Float) {
    val colors = AppTheme.extended
    val dark = AppTheme.isDark
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val band = min(w, h) * 0.38f
        val ink = colors.ornament.copy(alpha = (if (dark) 0.28f else 0.18f) * intensity)
        // 星环的主体落在画面外，仅露出贴着上下切图的弧段。
        for (index in 0..2) {
            val inset = index * 7.dp.toPx()
            drawOval(
                color = ink.copy(alpha = ink.alpha * (1f - index * 0.22f)),
                topLeft = Offset(-w * 0.20f, -band * 0.58f + inset),
                size = Size(w * 1.45f, band),
                style = Stroke(0.8.dp.toPx()),
            )
            drawOval(
                color = ink.copy(alpha = ink.alpha * (1f - index * 0.22f)),
                topLeft = Offset(-w * 0.22f, h - band * 0.54f + inset),
                size = Size(w * 1.36f, band),
                style = Stroke(0.8.dp.toPx()),
            )
        }
        // 弧线从边缘舒展，在进入内容区前淡出，不形成贴边的竖向轨道。
        repeat(2) { index ->
            val inset = index * 8.dp.toPx()
            val leftArc = Path().apply {
                moveTo(-w * 0.08f, h * 0.30f + inset)
                cubicTo(w * 0.02f, h * 0.36f + inset, w * 0.22f, h * 0.38f + inset, w * 0.43f, h * 0.34f + inset)
            }
            val rightArc = Path().apply {
                moveTo(w * 0.58f, h * 0.68f + inset)
                cubicTo(w * 0.80f, h * 0.71f + inset, w * 0.98f, h * 0.66f + inset, w * 1.08f, h * 0.60f + inset)
            }
            val arcInk = ink.copy(alpha = ink.alpha * (0.55f - index * 0.15f))
            drawPath(
                leftArc,
                Brush.horizontalGradient(listOf(arcInk, Color.Transparent), endX = w * 0.43f),
                style = Stroke(0.8.dp.toPx()),
            )
            drawPath(
                rightArc,
                Brush.horizontalGradient(listOf(Color.Transparent, arcInk), startX = w * 0.58f, endX = w),
                style = Stroke(0.8.dp.toPx()),
            )
        }
        translate(w * 0.085f, h * 0.13f) {
            drawPath(sparklePath(Size(17.dp.toPx(), 17.dp.toPx())), colors.sparkle.copy(alpha = 0.42f * intensity))
        }
        translate(w * 0.88f, h * 0.88f) {
            drawPath(sparklePath(Size(22.dp.toPx(), 22.dp.toPx())), colors.sparkle.copy(alpha = 0.50f * intensity))
        }
    }
}

/** 玻璃卡片背后的柔光。用径向渐变而不是投影，避免“描边 + 大阴影”的廉价层级。 */
@Composable
fun HaloGlow(
    modifier: Modifier = Modifier,
    color: Color = AppTheme.extended.glow,
    strength: Float = 0.30f,
) {
    Canvas(modifier) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = strength),
                    color.copy(alpha = strength * 0.35f),
                    color.copy(alpha = 0f),
                ),
                center = center,
                radius = size.minDimension * 0.62f,
            ),
        )
    }
}

/** 四角星芒。参考图的标志性装饰，只有四个尖角，腰身内收。 */
@Composable
fun Sparkle(
    sizeDp: Dp,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.extended.sparkle,
    alpha: Float = 1f,
    twinkle: Boolean = true,
    phaseOffset: Float = 0f,
) {
    val reducedMotion = LocalReducedMotion.current
    // 只在绘制阶段读取动画值：每帧触发 draw 失效，而不是整棵子树重组。
    val pulse = if (twinkle && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "sparkle")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(3200, easing = LinearEasing, delayMillis = (phaseOffset * 1200).toInt()),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse",
        )
    } else {
        null
    }
    Canvas(modifier.size(sizeDp)) {
        val wave = pulse?.value ?: 0.55f
        val glow = 0.72f + 0.28f * wave
        drawPath(
            path = sparklePath(this.size),
            color = color.copy(alpha = (alpha * glow).coerceIn(0f, 1f)),
        )
    }
}

private fun sparklePath(size: Size): Path {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = min(size.width, size.height) / 2f
    val waist = radius * 0.13f
    return Path().apply {
        moveTo(cx, cy - radius)
        quadraticTo(cx + waist, cy - waist, cx + radius, cy)
        quadraticTo(cx + waist, cy + waist, cx, cy + radius)
        quadraticTo(cx - waist, cy + waist, cx - radius, cy)
        quadraticTo(cx - waist, cy - waist, cx, cy - radius)
        close()
    }
}

/** 3×3 点阵。 */
@Composable
fun DotGrid(
    modifier: Modifier = Modifier,
    columns: Int = 3,
    rows: Int = 3,
    dotSize: Dp = 2.dp,
    gap: Dp = 7.dp,
    color: Color = AppTheme.extended.ornament,
    alpha: Float = 0.55f,
) {
    val dotPx = with(LocalDensity.current) { dotSize.toPx() }
    val gapPx = with(LocalDensity.current) { gap.toPx() }
    Canvas(modifier) {
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = dotPx / 2f,
                    center = Offset(
                        x = dotPx / 2f + column * (dotPx + gapPx),
                        y = dotPx / 2f + row * (dotPx + gapPx),
                    ),
                )
            }
        }
    }
}

/** 交错棋盘短条。参考图右上、左下各有一段。 */
@Composable
fun CheckerStrip(
    modifier: Modifier = Modifier,
    cells: Int = 5,
    cellSize: Dp = 4.dp,
    color: Color = AppTheme.extended.ornament,
    alpha: Float = 0.75f,
) {
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    Canvas(modifier) {
        for (row in 0 until 2) {
            for (cell in 0 until cells) {
                if ((cell + row) % 2 == 0) {
                    drawRect(
                        color = color.copy(alpha = alpha),
                        topLeft = Offset(
                            x = cell * (cellPx + cellPx * 0.35f) + row * cellPx * 0.55f,
                            y = row * (cellPx + cellPx * 0.35f),
                        ),
                        size = Size(cellPx, cellPx),
                    )
                }
            }
        }
    }
}

/**
 * 背景星座：竖向胶片条 + 点阵 + 棋盘 + 星芒。
 * 位置使用比例而非固定值，随卡片尺寸一起缩放。
 */
@Composable
fun OrnamentField(
    modifier: Modifier = Modifier,
    showBars: Boolean = true,
    sparkles: List<SparkleSpot> = DefaultSparkles,
    intensity: Float = 1f,
) {
    val extended = AppTheme.extended
    val ornament = extended.ornament
    val dark = AppTheme.isDark
    val lineAlpha = (if (dark) 0.60f else 0.32f) * intensity

    BoxWithConstraints(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            if (showBars) {
                // 叠放的宽薄片与窄亮边，轮廓保留在主卡片两侧。
                filmBar(w * 0.12f, h * 0.22f, w * 0.22f, h * 0.59f, extended.auroraCore, (if (dark) 0.70f else 0.32f) * intensity)
                filmBar(w * 0.64f, h * 0.10f, w * 0.23f, h * 0.57f, extended.auroraPale, (if (dark) 0.64f else 0.56f) * intensity)
                filmBar(w * 0.76f, h * 0.59f, w * 0.13f, h * 0.25f, extended.filmC, 0.80f * intensity)
                filmBar(w * 0.105f, h * 0.13f, w * 0.09f, h * 0.16f, extended.auroraPale, 0.52f * intensity)
                filmBar(w * 0.145f, h * 0.17f, w * 0.055f, h * 0.22f, extended.auroraCrest, 0.68f * intensity)
                filmBar(w * 0.82f, h * 0.69f, w * 0.055f, h * 0.12f, extended.auroraCore, 0.65f * intensity)
                // 两侧短条沿用相同材质。
                filmBar(w * 0.085f, h * 0.16f, 7.dp.toPx(), h * 0.19f, extended.filmC, 0.85f * intensity)
                filmBar(w * 0.085f, h * 0.40f, 12.dp.toPx(), h * 0.11f, extended.filmB, 0.95f * intensity)
                filmBar(w * 0.085f, h * 0.60f, 5.dp.toPx(), h * 0.16f, extended.filmA, 0.8f * intensity)
                // 右列。
                filmBar(w * 0.90f, h * 0.30f, 9.dp.toPx(), h * 0.17f, extended.filmB, 0.9f * intensity)
                filmBar(w * 0.90f, h * 0.55f, 6.dp.toPx(), h * 0.21f, extended.filmA, 0.75f * intensity)
                filmBar(w * 0.90f, h * 0.80f, 11.dp.toPx(), h * 0.09f, extended.filmC, 0.6f * intensity)
                // 银白 / 青灰的窄条与断线，给柔软的水彩增加清晰节奏。
                filmBar(w * 0.055f, h * 0.065f, 3.dp.toPx(), h * 0.18f, ornament, lineAlpha)
                filmBar(w * 0.80f, h * 0.095f, 6.dp.toPx(), h * 0.075f, ornament, lineAlpha)
                filmBar(w * 0.91f, h * 0.48f, 7.dp.toPx(), h * 0.11f, ornament, lineAlpha)
                filmBar(w * 0.07f, h * 0.70f, 4.dp.toPx(), h * 0.10f, extended.auroraCrest, 0.8f * intensity)
                drawLine(ornament.copy(alpha = lineAlpha), Offset(w * 0.94f, h * 0.78f), Offset(w * 0.94f, h * 0.94f), 0.8.dp.toPx())
                drawLine(ornament.copy(alpha = lineAlpha * 0.7f), Offset(w * 0.96f, h * 0.85f), Offset(w * 0.96f, h * 0.94f), 0.6.dp.toPx())
            }

            // 点阵。
            drawDotGrid(Offset(w * 0.17f, h * 0.10f), ornament, 0.55f * intensity)
            drawDotGrid(Offset(w * 0.79f, h * 0.62f), ornament, 0.42f * intensity)
            drawDotGrid(Offset(w * 0.14f, h * 0.80f), ornament, 0.35f * intensity)
            // 棋盘短条。
            drawCheckerStrip(Offset(w * 0.70f, h * 0.055f), ornament, 0.7f * intensity)
            drawCheckerStrip(Offset(w * 0.03f, h * 0.88f), ornament, 0.5f * intensity)
        }

        sparkles.forEach { spot ->
            Sparkle(
                sizeDp = spot.size,
                color = extended.sparkle,
                alpha = spot.alpha * intensity,
                phaseOffset = spot.phase,
                modifier = Modifier.offset(
                    x = maxWidth * spot.x - spot.size / 2,
                    y = maxHeight * spot.y - spot.size / 2,
                ),
            )
        }
    }
}

private fun DrawScope.filmBar(
    x: Float,
    y: Float,
    widthPx: Float,
    heightPx: Float,
    color: Color,
    alpha: Float,
) {
    val corner = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
    // 同色深浅变化保留半透明叠片感，不使用发光外边或阴影。
    drawRoundRect(
        brush = Brush.linearGradient(
            0f to color.copy(alpha = alpha * 0.82f),
            0.55f to color.copy(alpha = alpha * 0.55f),
            1f to color.copy(alpha = alpha * 0.80f),
            start = Offset(x, y),
            end = Offset(x + widthPx, y + heightPx),
        ),
        topLeft = Offset(x, y),
        size = Size(widthPx, heightPx),
        cornerRadius = corner,
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = alpha * 0.10f), Color.Transparent),
            startY = y, endY = y + heightPx * 0.8f,
        ),
        topLeft = Offset(x, y),
        size = Size(widthPx, heightPx),
        cornerRadius = corner,
        style = Stroke(width = 0.5.dp.toPx()),
    )
}

private fun DrawScope.drawDotGrid(origin: Offset, color: Color, alpha: Float) {
    val dot = 2.dp.toPx()
    val gap = 7.dp.toPx()
    for (row in 0 until 3) {
        for (column in 0 until 3) {
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = dot / 2f,
                center = Offset(
                    origin.x + column * (dot + gap),
                    origin.y + row * (dot + gap),
                ),
            )
        }
    }
}

private fun DrawScope.drawCheckerStrip(origin: Offset, color: Color, alpha: Float) {
    val cell = 4.dp.toPx()
    val step = cell * 1.35f
    for (row in 0 until 2) {
        for (index in 0 until 5) {
            if ((index + row) % 2 == 0) {
                drawRect(
                    color = color.copy(alpha = alpha),
                    topLeft = Offset(
                        origin.x + index * step + row * cell * 0.55f,
                        origin.y + row * step,
                    ),
                    size = Size(cell, cell),
                )
            }
        }
    }
}

/**
 * 轨道线：低透明度细椭圆弧，位于主卡片后方，不穿过正文与操作。
 */
@Composable
fun OrbitOverlay(
    modifier: Modifier = Modifier,
    color: Color = AppTheme.extended.ornament,
    alpha: Float = 0.58f,
) {
    val dark = AppTheme.isDark
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val opacity = alpha * if (dark) 1f else 0.58f
        // 三道相近而不重合的轨道，收拢在画面内，形成参考图的斜向星环。
        rotate(degrees = -29f, pivot = Offset(w * 0.5f, h * 0.48f)) {
            repeat(3) { index ->
                drawOval(
                    color = color.copy(alpha = opacity * (1f - index * 0.20f)),
                    topLeft = Offset(w * (0.015f + index * 0.014f), h * 0.48f - w * (0.13f + index * 0.018f)),
                    size = Size(w * (0.97f - index * 0.018f), w * (0.26f + index * 0.036f)),
                    style = Stroke(width = (1.1f - index * 0.15f).dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        // 下方两道开放弧线承接底部水彩，避免另一个完整圆环抢走主视觉。
        repeat(2) { index ->
            val arc = Path().apply {
                moveTo(w * (0.10f + index * 0.04f), h * 0.88f)
                cubicTo(w * 0.32f, h * 0.73f, w * 0.72f, h * 0.79f, w * (0.90f + index * 0.025f), h * 0.62f)
            }
            drawPath(arc, color.copy(alpha = opacity * (0.50f - index * 0.15f)), style = Stroke(0.8.dp.toPx()))
        }
    }
}

data class SparkleSpot(
    val x: Float,
    val y: Float,
    val size: Dp,
    val alpha: Float = 0.9f,
    val phase: Float = 0f,
)

private val DefaultSparkles = listOf(
    SparkleSpot(0.10f, 0.34f, 22.dp, 0.85f, 0.1f),
    SparkleSpot(0.89f, 0.22f, 25.dp, 0.95f, 0.45f),
    SparkleSpot(0.06f, 0.65f, 12.dp, 0.65f, 0.7f),
    SparkleSpot(0.85f, 0.74f, 22.dp, 0.85f, 0.25f),
    SparkleSpot(0.62f, 0.11f, 7.dp, 0.45f, 0.9f),
)

/**
 * 一次性入场：轻微上浮。
 *
 * - 只做位移、不做整体透明度：内容第一帧就完整可见，动画停摆时不会出现空白区块。
 * - 进度在 graphicsLayer 里读取，只让合成层更新，不触发子树重组。
 */
@Composable
fun Modifier.entrance(delayMs: Int = 0): Modifier {
    val reducedMotion = LocalReducedMotion.current
    val progress = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            progress.snapTo(1f)
        } else if (progress.value < 1f) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = Motion.Slow,
                    delayMillis = delayMs,
                    easing = Motion.EaseOutQuint,
                ),
            )
        }
    }
    return this.graphicsLayer {
        translationY = (1f - progress.value) * 18.dp.toPx()
    }
}
