package com.example.video2gif.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.video2gif.domain.ConversionRecipe
import com.example.video2gif.domain.CropAspect
import com.example.video2gif.domain.CropSpec
import com.example.video2gif.domain.SourceVideo
import com.example.video2gif.domain.formatResolution
import com.example.video2gif.domain.outputSpec

/**
 * 裁剪编辑器：取景框坐标相对旋转后的画面；拖动四角保持所选比例，
 * 拖动框内移动取景框。数值结果在底部实时显示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropEditorSheet(
    frame: Bitmap?,
    loading: Boolean,
    source: SourceVideo,
    recipe: ConversionRecipe,
    onRotate: (Int) -> Unit,
    onApply: (CropSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    var working by remember { mutableStateOf(recipe.crop) }
    LaunchedEffect(recipe.crop, recipe.userRotation) {
        working = recipe.crop
    }
    val output = recipe.copy(crop = working).outputSpec(source)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("裁剪与旋转", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "拖动四角调整取景框，拖动框内移动画面。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (output != null) {
                    Text(
                        formatResolution(output.width, output.height),
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            CropCanvas(
                frame = frame,
                loading = loading,
                working = working,
                source = source,
                onWorking = { working = it },
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrackChip("原比例", working.aspect == CropAspect.Original) {
                    working = CropSpec.centered(CropAspect.Original, frame?.width ?: 1, frame?.height ?: 1)
                }
                TrackChip("1:1", working.aspect == CropAspect.Square) {
                    working = CropSpec.centered(CropAspect.Square, frame?.width ?: 1, frame?.height ?: 1)
                }
                TrackChip("4:3", working.aspect == CropAspect.FourThree) {
                    working = CropSpec.centered(CropAspect.FourThree, frame?.width ?: 1, frame?.height ?: 1)
                }
                TrackChip("9:16", working.aspect == CropAspect.NineSixteen) {
                    working = CropSpec.centered(CropAspect.NineSixteen, frame?.width ?: 1, frame?.height ?: 1)
                }
                TrackChip("自由", working.aspect == CropAspect.Free) {
                    working = working.copy(aspect = CropAspect.Free)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "旋转",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                SegmentedOptions(
                    options = listOf(0, 90, 180, 270).map {
                        SegmentOption(it, "$it°")
                    },
                    selected = recipe.userRotation,
                    onSelect = onRotate,
                    modifier = Modifier.weight(1f),
                    compact = true,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                TextButton(onClick = { onApply(working) }) {
                    Text("应用裁剪", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TrackChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
private fun CropCanvas(
    frame: Bitmap?,
    loading: Boolean,
    working: CropSpec,
    source: SourceVideo,
    onWorking: (CropSpec) -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(268.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0B090B)),
    ) {
        if (frame == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (loading) "正在准备裁剪画面…" else "暂时无法读取画面",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB5AFB3),
                )
            }
            return@BoxWithConstraints
        }
        val imageAspect = frame.width.toFloat() / frame.height.coerceAtLeast(1)
        val boxAspect = maxWidth.value / 268f
        val fitWidth = if (boxAspect > imageAspect) 268f * imageAspect else maxWidth.value
        val fitHeight = if (boxAspect > imageAspect) 268f else maxWidth.value / imageAspect
        val left = (maxWidth.value - fitWidth) / 2f
        val top = (268f - fitHeight) / 2f
        val fitWidthDp = fitWidth.dp
        val fitHeightDp = fitHeight.dp
        val leftDp = left.dp
        val topDp = top.dp
        val currentWorking by rememberUpdatedState(working)
        val currentOnWorking by rememberUpdatedState(onWorking)
        val fitWidthPx = with(density) { fitWidthDp.toPx() }
        val fitHeightPx = with(density) { fitHeightDp.toPx() }

        Image(
            bitmap = frame.asImageBitmap(),
            contentDescription = "裁剪画面，拖动四角调整取景框",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(Modifier.fillMaxSize()) {
            val cropLeft = leftDp.toPx() + currentWorking.left * fitWidthPx
            val cropTop = topDp.toPx() + currentWorking.top * fitHeightPx
            val cropWidth = currentWorking.width * fitWidthPx
            val cropHeight = currentWorking.height * fitHeightPx
            val scrim = Color.Black.copy(alpha = 0.55f)
            drawRect(scrim, Offset(0f, 0f), Size(size.width, cropTop))
            drawRect(scrim, Offset(0f, cropTop + cropHeight), Size(size.width, size.height - cropTop - cropHeight))
            drawRect(scrim, Offset(0f, cropTop), Size(cropLeft, cropHeight))
            drawRect(scrim, Offset(cropLeft + cropWidth, cropTop), Size(size.width - cropLeft - cropWidth, cropHeight))
            drawRect(
                color = Color.White,
                topLeft = Offset(cropLeft, cropTop),
                size = Size(cropWidth, cropHeight),
                style = Stroke(width = 1.5.dp.toPx()),
            )
            val grid = Color.White.copy(alpha = 0.35f)
            for (i in 1..2) {
                val x = cropLeft + cropWidth * i / 3f
                drawLine(grid, Offset(x, cropTop), Offset(x, cropTop + cropHeight), 1.dp.toPx())
                val y = cropTop + cropHeight * i / 3f
                drawLine(grid, Offset(cropLeft, y), Offset(cropLeft + cropWidth, y), 1.dp.toPx())
            }
        }
        Box(
            modifier = Modifier
                .offset(x = leftDp + (working.left * fitWidth).dp, y = topDp + (working.top * fitHeight).dp)
                .width((working.width * fitWidth).dp)
                .height((working.height * fitHeight).dp)
                .pointerInput(fitWidthPx, fitHeightPx, working.aspect) {
                    var start = CropSpec()
                    var accumulatedX = 0f
                    var accumulatedY = 0f
                    detectDragGestures(
                        onDragStart = {
                            start = currentWorking
                            accumulatedX = 0f
                            accumulatedY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedX += dragAmount.x
                            accumulatedY += dragAmount.y
                            val dx = accumulatedX / fitWidthPx
                            val dy = accumulatedY / fitHeightPx
                            val width = start.width
                            val height = start.height
                            val newLeft = (start.left + dx).coerceIn(0f, 1f - width)
                            val newTop = (start.top + dy).coerceIn(0f, 1f - height)
                            currentOnWorking(
                                start.copy(
                                    left = newLeft,
                                    top = newTop,
                                    right = newLeft + width,
                                    bottom = newTop + height,
                                ),
                            )
                        },
                    )
                },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .border(1.5.dp, Color.White, RoundedCornerShape(1.dp)),
            )
            CropCorner(
                alignment = Alignment.TopStart,
                fitWidthPx = fitWidthPx,
                fitHeightPx = fitHeightPx,
                imageWidth = frame.width,
                imageHeight = frame.height,
                corner = Corner.TopStart,
                working = currentWorking,
                onWorking = currentOnWorking,
            )
            CropCorner(
                alignment = Alignment.TopEnd,
                fitWidthPx = fitWidthPx,
                fitHeightPx = fitHeightPx,
                imageWidth = frame.width,
                imageHeight = frame.height,
                corner = Corner.TopEnd,
                working = currentWorking,
                onWorking = currentOnWorking,
            )
            CropCorner(
                alignment = Alignment.BottomStart,
                fitWidthPx = fitWidthPx,
                fitHeightPx = fitHeightPx,
                imageWidth = frame.width,
                imageHeight = frame.height,
                corner = Corner.BottomStart,
                working = currentWorking,
                onWorking = currentOnWorking,
            )
            CropCorner(
                alignment = Alignment.BottomEnd,
                fitWidthPx = fitWidthPx,
                fitHeightPx = fitHeightPx,
                imageWidth = frame.width,
                imageHeight = frame.height,
                corner = Corner.BottomEnd,
                working = currentWorking,
                onWorking = currentOnWorking,
            )
        }
    }
}

private enum class Corner { TopStart, TopEnd, BottomStart, BottomEnd }

@Composable
private fun BoxScope.CropCorner(
    alignment: Alignment,
    fitWidthPx: Float,
    fitHeightPx: Float,
    imageWidth: Int,
    imageHeight: Int,
    corner: Corner,
    working: CropSpec,
    onWorking: (CropSpec) -> Unit,
) {
    val currentWorking by rememberUpdatedState(working)
    val currentOnWorking by rememberUpdatedState(onWorking)
    val currentAspect = working.aspect
    Box(
        modifier = Modifier
            .align(alignment)
            .size(48.dp)
            .pointerInput(fitWidthPx, fitHeightPx, currentAspect) {
                var start = CropSpec()
                var accumulatedX = 0f
                var accumulatedY = 0f
                detectDragGestures(
                    onDragStart = {
                        start = currentWorking
                        accumulatedX = 0f
                        accumulatedY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedX += dragAmount.x
                        accumulatedY += dragAmount.y
                        val dx = accumulatedX / fitWidthPx
                        val dy = accumulatedY / fitHeightPx
                        val updated = when (corner) {
                            Corner.TopStart -> CropSpec.fromEdgeDrag(
                                aspect = start.aspect,
                                sourceWidth = imageWidth,
                                sourceHeight = imageHeight,
                                anchorLeft = start.right,
                                anchorTop = start.bottom,
                                right = start.left + dx,
                                bottom = start.top + dy,
                            )
                            Corner.TopEnd -> CropSpec.fromEdgeDrag(
                                aspect = start.aspect,
                                sourceWidth = imageWidth,
                                sourceHeight = imageHeight,
                                anchorLeft = start.left,
                                anchorTop = start.bottom,
                                right = start.right + dx,
                                bottom = start.top + dy,
                            )
                            Corner.BottomStart -> CropSpec.fromEdgeDrag(
                                aspect = start.aspect,
                                sourceWidth = imageWidth,
                                sourceHeight = imageHeight,
                                anchorLeft = start.right,
                                anchorTop = start.top,
                                right = start.left + dx,
                                bottom = start.bottom + dy,
                            )
                            Corner.BottomEnd -> CropSpec.fromEdgeDrag(
                                aspect = start.aspect,
                                sourceWidth = imageWidth,
                                sourceHeight = imageHeight,
                                anchorLeft = start.left,
                                anchorTop = start.top,
                                right = start.right + dx,
                                bottom = start.bottom + dy,
                            )
                        }
                        currentOnWorking(updated)
                    },
                )
            }
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(14.dp)
                .background(Color.White, CircleShape),
        )
    }
}
