package com.example.video2gif.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.video2gif.ui.theme.AppTheme
import com.example.video2gif.ui.theme.Motion
import kotlin.math.min

/*
 * 氛围层：极光渐变 + 几何装饰。
 *
 * 规则：
 * - 只做背景与装饰，从不承载文字或接收触摸。
 * - 极光用一次性绘制的静态 Canvas，靠 graphicsLayer 做慢速漂移，避免每帧重绘渐变。
 * - 系统关闭动画时，所有循环动画停在固定相位，装饰仍然可见。
 */

/** 极光背景。铺满父容器，内部包含顶部光冠、右下辉光与底部波。 */
@Composable
fun AuroraBackdrop(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
) {
    val extended = AppTheme.extended
    val dark = AppTheme.isDark
    val background = MaterialTheme.colorScheme.background
    val reducedMotion = LocalReducedMotion.current

    val phase = if (reducedMotion) {
        0.5f
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
        ).value
    }

    val crestAlpha = (if (dark) 0.40f else 0.30f) * intensity
    val coreAlpha = (if (dark) 0.42f else 0.32f) * intensity
    val hazeAlpha = (if (dark) 0.24f else 0.16f) * intensity

    Canvas(
        modifier = modifier.graphicsLayer {
            // 只做合成层变换：不触发重绘，长周期漂移几乎不耗 GPU。
            scaleX = 1.05f + phase * 0.03f
            scaleY = 1.03f + phase * 0.02f
            translationX = -size.width * 0.012f * phase
            translationY = size.height * 0.008f * (phase - 0.5f)
        },
    ) {
        val w = size.width
        val h = size.height
        drawRect(background)

        // 顶部光冠：一片从屏幕上方压下来的薄荷光带。
        val crest = Path().apply {
            moveTo(0f, -h * 0.04f)
            lineTo(w, -h * 0.04f)
            lineTo(w, h * 0.17f)
            cubicTo(w * 0.82f, h * 0.30f, w * 0.62f, h * 0.04f, w * 0.38f, h * 0.115f)
            cubicTo(w * 0.22f, h * 0.165f, w * 0.10f, h * 0.075f, 0f, h * 0.145f)
            close()
        }
        drawPath(
            path = crest,
            brush = Brush.verticalGradient(
                colors = listOf(
                    extended.auroraCrest.copy(alpha = crestAlpha),
                    extended.auroraCrest.copy(alpha = crestAlpha * 0.55f),
                    extended.auroraCrest.copy(alpha = 0f),
                ),
                startY = 0f,
                endY = h * 0.30f,
            ),
        )
        // 光冠下缘一条极细的亮边，让“光带”有明确的轮廓而不是一团雾。
        val crestEdge = Path().apply {
            moveTo(0f, h * 0.145f)
            cubicTo(w * 0.10f, h * 0.075f, w * 0.22f, h * 0.165f, w * 0.38f, h * 0.115f)
            cubicTo(w * 0.62f, h * 0.04f, w * 0.82f, h * 0.30f, w, h * 0.17f)
        }
        drawPath(
            path = crestEdge,
            color = extended.auroraPale.copy(alpha = crestAlpha * 0.55f),
            style = Stroke(width = 1.dp.toPx()),
        )

        // 右上方的高光核心，让光冠有一处更亮的落点。
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    extended.auroraPale.copy(alpha = crestAlpha * 0.85f),
                    extended.auroraCore.copy(alpha = crestAlpha * 0.35f),
                    extended.auroraCore.copy(alpha = 0f),
                ),
                center = Offset(w * 0.86f, h * 0.03f),
                radius = w * 0.78f,
            ),
        )

        // 底部波光：与顶部对称，颜色更偏青；左下角再补一颗亮核，
        // 让页面底部有重量，而不是一条均匀的暗带。
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    extended.auroraCrest.copy(alpha = coreAlpha * 0.88f),
                    extended.auroraCore.copy(alpha = coreAlpha * 0.34f),
                    extended.auroraCore.copy(alpha = 0f),
                ),
                center = Offset(w * 0.16f, h * 1.02f),
                radius = w * 0.72f,
            ),
        )
        val waveTop = h * 0.66f
        val wave = Path().apply {
            moveTo(0f, h)
            lineTo(0f, waveTop + h * 0.06f)
            cubicTo(w * 0.22f, waveTop - h * 0.05f, w * 0.46f, waveTop + h * 0.10f, w * 0.68f, waveTop + h * 0.045f)
            cubicTo(w * 0.84f, waveTop + h * 0.005f, w * 0.94f, waveTop + h * 0.075f, w, waveTop + h * 0.03f)
            lineTo(w, h)
            close()
        }
        drawPath(
            path = wave,
            brush = Brush.verticalGradient(
                colors = listOf(
                    extended.auroraCore.copy(alpha = 0f),
                    extended.auroraDeep.copy(alpha = coreAlpha * 0.45f),
                    extended.auroraCrest.copy(alpha = coreAlpha * 0.95f),
                ),
                startY = waveTop,
                endY = h,
            ),
        )
        // 波峰轮廓线：一条随波起伏的薄荷细线。
        val waveEdge = Path().apply {
            moveTo(0f, waveTop + h * 0.06f)
            cubicTo(w * 0.22f, waveTop - h * 0.05f, w * 0.46f, waveTop + h * 0.10f, w * 0.68f, waveTop + h * 0.045f)
            cubicTo(w * 0.84f, waveTop + h * 0.005f, w * 0.94f, waveTop + h * 0.075f, w, waveTop + h * 0.03f)
        }
        drawPath(
            path = waveEdge,
            color = extended.auroraCrest.copy(alpha = coreAlpha * 0.70f),
            style = Stroke(width = 1.2.dp.toPx()),
        )
        // 波峰上的一层薄光，给底边一点通透感。
        drawPath(
            path = wave,
            brush = Brush.radialGradient(
                colors = listOf(
                    extended.auroraPale.copy(alpha = coreAlpha * 0.18f),
                    extended.auroraPale.copy(alpha = 0f),
                ),
                center = Offset(w * 0.7f, h * 0.95f),
                radius = w * 0.8f,
            ),
        )

        // 中段薄雾，避免上下两块光在中间断开。
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    extended.auroraDeep.copy(alpha = hazeAlpha * 0.35f),
                    extended.auroraDeep.copy(alpha = 0f),
                ),
                center = Offset(w * 0.12f, h * 0.58f),
                radius = w * 0.62f,
            ),
        )

        if (dark) {
            // 只在画面中段压暗一层，模拟极光之间更深的夜空；
            // 上下两端保持亮，避免把光冠与波光一起压灰。
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        background.copy(alpha = 0.42f),
                        background.copy(alpha = 0.30f),
                        Color.Transparent,
                    ),
                    startY = h * 0.14f,
                    endY = h * 0.86f,
                ),
            )
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

    BoxWithConstraints(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            if (showBars) {
                // 左列：三层不同亮度，模仿参考图里错落的胶片。
                filmBar(w * 0.085f, h * 0.16f, 7.dp.toPx(), h * 0.19f, extended.filmC, 0.85f * intensity)
                filmBar(w * 0.085f, h * 0.40f, 12.dp.toPx(), h * 0.11f, extended.filmB, 0.95f * intensity)
                filmBar(w * 0.085f, h * 0.60f, 5.dp.toPx(), h * 0.16f, extended.filmA, 0.8f * intensity)
                // 右列。
                filmBar(w * 0.90f, h * 0.30f, 9.dp.toPx(), h * 0.17f, extended.filmB, 0.9f * intensity)
                filmBar(w * 0.90f, h * 0.55f, 6.dp.toPx(), h * 0.21f, extended.filmA, 0.75f * intensity)
                filmBar(w * 0.90f, h * 0.80f, 11.dp.toPx(), h * 0.09f, extended.filmC, 0.6f * intensity)
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
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.25f)),
        ),
        topLeft = Offset(x, y),
        size = Size(widthPx, heightPx),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(widthPx / 2f, widthPx / 2f),
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
 * 轨道线：两条细椭圆弧，从卡片前方掠过。这是参考图里最强的“宇宙感”来源，
 * 因此用 1dp 线宽、低透明度绘制在内容之上，但不拦截任何触摸。
 */
@Composable
fun OrbitOverlay(
    modifier: Modifier = Modifier,
    color: Color = AppTheme.extended.ornament,
    alpha: Float = 0.30f,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
        rotate(degrees = -16f, pivot = center) {
            drawOval(
                color = color.copy(alpha = alpha),
                topLeft = Offset(-w * 0.10f, h * 0.30f),
                size = Size(w * 1.20f, h * 0.30f),
                style = stroke,
            )
            drawOval(
                color = color.copy(alpha = alpha * 0.55f),
                topLeft = Offset(w * 0.02f, h * 0.355f),
                size = Size(w * 0.96f, h * 0.22f),
                style = stroke,
            )
        }
        rotate(degrees = 12f, pivot = center) {
            drawOval(
                color = color.copy(alpha = alpha * 0.5f),
                topLeft = Offset(w * 0.05f, h * 0.66f),
                size = Size(w * 0.90f, h * 0.20f),
                style = stroke,
            )
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
    SparkleSpot(0.20f, 0.20f, 12.dp, 0.85f, 0.1f),
    SparkleSpot(0.83f, 0.27f, 16.dp, 0.95f, 0.45f),
    SparkleSpot(0.11f, 0.66f, 10.dp, 0.6f, 0.7f),
    SparkleSpot(0.80f, 0.78f, 13.dp, 0.8f, 0.25f),
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
