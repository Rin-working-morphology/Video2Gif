package com.example.video2gif.ui.screens

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.video2gif.domain.RunItem
import com.example.video2gif.ui.AppViewModel
import com.example.video2gif.ui.components.AppTopBar
import com.example.video2gif.ui.components.AuroraBackdrop
import com.example.video2gif.ui.components.GlassPanel
import com.example.video2gif.ui.components.Hairline
import com.example.video2gif.ui.components.Sparkle
import com.example.video2gif.ui.theme.PanelShape
import com.example.video2gif.ui.components.BottomGlassBar
import com.example.video2gif.ui.components.MetaText
import com.example.video2gif.ui.components.PrimaryAction
import com.example.video2gif.ui.components.SecondaryAction
import com.example.video2gif.ui.components.shareGif
import com.example.video2gif.ui.components.shareGifs
import com.example.video2gif.ui.theme.AppTheme
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ResultScreen(vm: AppViewModel) {
    val run by vm.run.collectAsStateWithLifecycle()
    val state = run ?: return
    val context = LocalContext.current
    val succeeded = state.items.filter { it.outputUri != null }
    val failed = state.items.filter {
        it.status == com.example.video2gif.domain.JobStatus.Failed ||
            it.status == com.example.video2gif.domain.JobStatus.Interrupted
    }
    var fullscreenUri by remember { mutableStateOf<String?>(null) }
    var barHeightPx by remember { mutableStateOf(0) }
    val bottomPadding = with(LocalDensity.current) { barHeightPx.toDp() } + 12.dp
    val extended = AppTheme.extended

    Box(Modifier.fillMaxSize()) {
        AuroraBackdrop(Modifier.fillMaxSize(), intensity = 0.9f)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            AppTopBar(
                title = "结果",
                subtitle = "${state.succeededCount} / ${state.items.size} 个完成",
                onBack = { vm.back() },
            )
            Column(
                Modifier
                    .weight(1f)
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = bottomPadding),
            ) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Sparkle(
                            sizeDp = 22.dp,
                            color = if (succeeded.isNotEmpty()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            alpha = 0.9f,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            when {
                                failed.isEmpty() -> "已完成 ${state.succeededCount} 个 GIF"
                                state.succeededCount == 0 -> "转换未完成"
                                else -> "完成 ${state.succeededCount} 个，${failed.size} 个未完成"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (succeeded.isNotEmpty()) {
                            "已保存，可直接分享。GIF 不包含声音。"
                        } else {
                            "失败项不会留下不完整的成品，可重试。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                }

                if (succeeded.isNotEmpty()) {
                    GlassPanel(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                        shape = PanelShape.card,
                    ) {
                        Column {
                            succeeded.forEachIndexed { index, item ->
                                ResultRow(
                                    item = item,
                                    onShare = {
                                        item.outputUri?.let { shareGif(context, it) }
                                    },
                                    onOpen = { fullscreenUri = item.outputUri },
                                )
                                if (index != succeeded.lastIndex) {
                                    Hairline(Modifier.padding(start = 92.dp))
                                }
                            }
                        }
                    }
                }

                if (failed.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            "未完成",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .clip(PanelShape.card)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
                    ) {
                        failed.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        item.error ?: "转换失败",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SecondaryAction(
                        text = "重试未完成项",
                        onClick = { vm.retryConversion() },
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }

        BottomGlassBar(Modifier.align(Alignment.BottomCenter).onSizeChanged { barHeightPx = it.height }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (succeeded.isNotEmpty()) {
                    SecondaryAction(
                        text = "分享全部",
                        icon = Icons.Rounded.IosShare,
                        onClick = {
                            shareGifs(
                                context,
                                succeeded.mapNotNull { it.outputUri },
                                "分享全部 GIF",
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                PrimaryAction(
                    text = "完成",
                    onClick = { vm.goHome() },
                )
            }
        }
    }

    val uri = fullscreenUri
    if (uri != null) {
        Dialog(
            onDismissRequest = { fullscreenUri = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF04090A))
                    .clickable { fullscreenUri = null },
                contentAlignment = Alignment.Center,
            ) {
                GifImage(
                    uriString = uri,
                    targetPx = 1400,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
                IconButton(
                    onClick = { fullscreenUri = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.White.copy(alpha = 0.14f), CircleShape),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ResultRow(item: RunItem, onShare: () -> Unit, onOpen: () -> Unit) {
    val extended = AppTheme.extended
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(extended.mediaStage),
        ) {
            item.outputUri?.let { uri ->
                GifImage(
                    uriString = uri,
                    targetPx = 200,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.outputName ?: item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            MetaText(
                "${com.example.video2gif.domain.formatBytes(item.outputSizeBytes)} · " +
                    "${item.locationLabel ?: "已保存"}",
            )
        }
        IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Rounded.IosShare,
                contentDescription = "分享 ${item.outputName ?: item.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 使用系统 ImageDecoder 播放真实 GIF（Android 9+）；旧版本退回静态首帧。
 * 目标尺寸限制在屏幕缩略图大小，避免为 72dp 预览解码整段 1080px 动画。
 */
@SuppressLint("ThreadConstraint")
@Composable
fun GifImage(uriString: String, targetPx: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val key = "$uriString|$targetPx"
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(0xFF04090A.toInt())
            }
        },
        update = { view ->
            if (view.tag != key) {
                view.tag = key
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(uriString))
                        val drawable = ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                            val width = info.size.width
                            val height = info.size.height
                            val scale = targetPx.toFloat() / max(width, height).coerceAtLeast(1)
                            if (scale < 1f) {
                                decoder.setTargetSize(
                                    (width * scale).roundToInt().coerceAtLeast(1),
                                    (height * scale).roundToInt().coerceAtLeast(1),
                                )
                            }
                        }
                        if (drawable is AnimatedImageDrawable) {
                            drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                            drawable.start()
                        }
                        view.setImageDrawable(drawable)
                    } else {
                        val options = BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }
                        val bitmap = context.contentResolver.openInputStream(Uri.parse(uriString))?.use {
                            BitmapFactory.decodeStream(it, null, options)
                        }
                        view.setImageBitmap(bitmap)
                    }
                }.onFailure {
                    view.setImageDrawable(null)
                }
            }
        },
        onRelease = { view ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                (view.drawable as? AnimatedImageDrawable)?.stop()
            }
        },
    )
}
