package com.example.video2gif.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.video2gif.domain.OutputSpec
import com.example.video2gif.media.PreviewFrame
import com.example.video2gif.ui.theme.AppTheme

/**
 * 视频舞台：内容优先，黑色衬底保证任何视频都可读。玻璃工具条只覆盖画面底部一小块，
 * 不作为装饰性模糊铺满整屏。
 */
@Composable
fun MediaStage(
    spec: OutputSpec?,
    frame: PreviewFrame?,
    loading: Boolean,
    error: String?,
    showOriginal: Boolean,
    isPlaying: Boolean,
    timecode: String,
    onToggleOriginal: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
    maxStageHeight: Dp = 420.dp,
) {
    val ratio = if (spec != null && spec.height > 0) {
        spec.width.toFloat() / spec.height
    } else {
        16f / 9f
    }
    val stageRatio = ratio.coerceIn(0.68f, 1.9f)
    val extended = AppTheme.extended
    val shape = RoundedCornerShape(16.dp)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stageHeight = (maxWidth / stageRatio)
            .coerceAtMost(maxStageHeight)
            .coerceAtLeast(minOf(180.dp, maxStageHeight))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stageHeight)
                .clip(shape)
                .background(extended.mediaStage)
                .border(1.dp, extended.mediaStageBorder, shape),
        ) {
            val rawBitmap = remember(frame?.raw) { frame?.raw?.asImageBitmap() }
            val processedBitmap = remember(frame?.processed) { frame?.processed?.asImageBitmap() }
            val shownBitmap = if (showOriginal) rawBitmap ?: processedBitmap else processedBitmap ?: rawBitmap
            if (shownBitmap != null) {
                Crossfade(
                    targetState = showOriginal,
                    animationSpec = tween(180),
                    label = "previewMode",
                ) { original ->
                    val bitmap = if (original) rawBitmap ?: processedBitmap else processedBitmap ?: rawBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = if (original) "原视频画面" else "效果预览画面",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    role = Role.Button,
                                    onClick = onTogglePlay,
                                ),
                        )
                    }
                }
            } else if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.7f),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(30.dp),
                    )
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        error ?: "正在准备预览",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB5AFB3),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }

            if (loading && shownBitmap != null) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                )
            }

            if (frame?.previewDownscaled == true) {
                GlassPanel(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .height(28.dp),
                    shape = CircleShape,
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "预览已降分辨率",
                            style = MaterialTheme.typography.labelMedium,
                            color = extended.glassText,
                        )
                    }
                }
            }

            GlassPanel(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp)
                    .fillMaxWidth()
                    .height(56.dp),
                backdrop = shownBitmap,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassPreviewToggle(
                        showOriginal = showOriginal,
                        onSelectOriginal = { if (!showOriginal) onToggleOriginal() },
                        onSelectEffect = { if (showOriginal) onToggleOriginal() },
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(role = Role.Button, onClick = onTogglePlay)
                            .semantics {
                                contentDescription = if (isPlaying) "暂停预览" else "播放预览"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = timecode,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFeatureSettings = "tnum",
                            fontWeight = FontWeight.Medium,
                        ),
                        color = extended.glassText,
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
    }
}

@Composable
private fun GlassPreviewToggle(
    showOriginal: Boolean,
    onSelectOriginal: () -> Unit,
    onSelectEffect: () -> Unit,
) {
    val extended = AppTheme.extended
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(extended.glassBorder.copy(alpha = 0.35f))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ToggleItem("原视频", selected = showOriginal, onClick = onSelectOriginal)
        ToggleItem("效果", selected = !showOriginal, onClick = onSelectEffect)
    }
}

@Composable
private fun ToggleItem(text: String, selected: Boolean, onClick: () -> Unit) {
    val extended = AppTheme.extended
    Box(
        modifier = Modifier
            .width(56.dp)
            .height(40.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else extended.glassText,
        )
    }
}
