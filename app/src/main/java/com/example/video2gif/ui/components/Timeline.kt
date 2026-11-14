package com.example.video2gif.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.video2gif.domain.formatDuration
import com.example.video2gif.domain.formatTimecode

/**
 * 缩略图时间轴：拖动两端调整截取范围，点按定位播放头。
 * 拖动手柄的触控宽度 40dp；精确输入由时间对话框提供，不以精细拖动作为唯一入口。
 */
@Composable
fun TrimTimeline(
    frames: List<Bitmap>,
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    playheadMs: Long,
    onSeek: (Long) -> Unit,
    onTrimChange: (Long, Long) -> Unit,
    onEditTimes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = durationMs.coerceAtLeast(1)
    val startFraction = (startMs.toFloat() / duration).coerceIn(0f, 1f)
    val endFraction = (endMs.toFloat() / duration).coerceIn(startFraction, 1f)
    val playheadFraction = (playheadMs.toFloat() / duration).coerceIn(0f, 1f)
    val selectedColor = MaterialTheme.colorScheme.primary

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            TimeLabel(
                label = "起点",
                value = formatTimecode(startMs),
                modifier = Modifier.weight(1f),
                onClick = onEditTimes,
                alignment = Alignment.Start,
            )
            Column(
                modifier = Modifier.weight(1.1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MetaText("共 ${formatDuration(endMs - startMs)}")
            }
            TimeLabel(
                label = "终点",
                value = formatTimecode(endMs),
                modifier = Modifier.weight(1f),
                onClick = onEditTimes,
                alignment = Alignment.End,
            )
        }
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(top = 4.dp),
        ) {
            val side = 20.dp
            val innerWidth = (maxWidth - side * 2).coerceAtLeast(40.dp)
            val density = LocalDensity.current
            val innerWidthPx = with(density) { innerWidth.toPx() }
            val stripHeight = 56.dp

            Box(
                modifier = Modifier
                    .offset(x = side, y = 0.dp)
                    .width(innerWidth)
                    .height(stripHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        RoundedCornerShape(12.dp),
                    )
                    .pointerInput(innerWidthPx, duration) {
                        detectTapGestures { position ->
                            val fraction = (position.x / innerWidthPx).coerceIn(0f, 1f)
                            onSeek((fraction * duration).toLong())
                        }
                    },
            ) {
                if (frames.isEmpty()) {
                    SkeletonBox(Modifier.fillMaxSize(), RoundedCornerShape(12.dp))
                } else {
                    Row(Modifier.fillMaxSize()) {
                        frames.forEach { frame ->
                            Image(
                                bitmap = frame.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
                // 选区外压低亮度，保留选区内的真实画面。
                val leftWidth = innerWidth * startFraction
                if (leftWidth > 0.dp) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .width(leftWidth)
                            .fillMaxHeight()
                            .background(Color(0xFF060F10).copy(alpha = 0.66f)),
                    )
                }
                val rightWidth = innerWidth * (1f - endFraction)
                if (rightWidth > 0.dp) {
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .width(rightWidth)
                            .fillMaxHeight()
                            .background(Color(0xFF060F10).copy(alpha = 0.66f)),
                    )
                }
                val playheadX = innerWidth * playheadFraction
                Box(
                    Modifier
                        .offset(x = playheadX - 1.5.dp)
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    Color.White.copy(alpha = 0.92f),
                                ),
                            ),
                        ),
                )
            }

            TrimHandle(
                offsetX = side + innerWidth * startFraction - 24.dp,
                timeMs = startMs,
                label = "截取起点",
                durationMs = duration,
                innerWidthPx = innerWidthPx,
                onDragValue = { value ->
                    val newStart = value.coerceIn(0L, endMs - 200L)
                    onTrimChange(newStart, endMs)
                },
                color = selectedColor,
            )
            TrimHandle(
                offsetX = side + innerWidth * endFraction - 24.dp,
                timeMs = endMs,
                label = "截取终点",
                durationMs = duration,
                innerWidthPx = innerWidthPx,
                onDragValue = { value ->
                    val newEnd = value.coerceIn(startMs + 200L, duration)
                    onTrimChange(startMs, newEnd)
                },
                color = selectedColor,
            )
        }
    }
}

@Composable
private fun TimeLabel(
    label: String,
    value: String,
    modifier: Modifier,
    onClick: () -> Unit,
    alignment: Alignment.Horizontal,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = alignment,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TimecodeText(value)
    }
}

@Composable
private fun TrimHandle(
    offsetX: Dp,
    timeMs: Long,
    label: String,
    durationMs: Long,
    innerWidthPx: Float,
    onDragValue: (Long) -> Unit,
    color: Color,
) {
    val currentTime by rememberUpdatedState(timeMs)
    val currentOnDrag by rememberUpdatedState(onDragValue)
    Box(
        modifier = Modifier
            .offset(x = offsetX)
            .width(48.dp)
            .height(56.dp)
            .semantics {
                contentDescription = "$label ${formatTimecode(timeMs)}"
                customActions = listOf(
                    CustomAccessibilityAction("$label 前移 0.1 秒") {
                        currentOnDrag(currentTime - 100L); true
                    },
                    CustomAccessibilityAction("$label 后移 0.1 秒") {
                        currentOnDrag(currentTime + 100L); true
                    },
                )
            }
            .pointerInput(innerWidthPx, durationMs) {
                var accumulated = 0f
                var anchor = 0L
                detectDragGestures(
                    onDragStart = {
                        accumulated = 0f
                        anchor = currentTime
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulated += dragAmount.x
                        val deltaMs = (accumulated / innerWidthPx * durationMs).toLong()
                        currentOnDrag(anchor + deltaMs)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(7.dp)
                .height(46.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.94f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(color),
            )
        }
    }
}
