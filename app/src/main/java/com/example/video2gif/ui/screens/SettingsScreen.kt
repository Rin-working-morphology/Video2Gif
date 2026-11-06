package com.example.video2gif.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.video2gif.domain.ThemeMode
import com.example.video2gif.ui.AppViewModel
import com.example.video2gif.ui.components.AppTopBar
import com.example.video2gif.ui.components.SegmentOption
import com.example.video2gif.ui.components.SegmentedOptions
import com.example.video2gif.ui.theme.AppTheme

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "1.0"
    val extended = AppTheme.extended

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppTopBar(title = "设置", onBack = { vm.back() })
        Column(
            Modifier
                .weight(1f)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            SettingsGroup("外观") {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "主题",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(10.dp))
                    SegmentedOptions(
                        options = listOf(
                            SegmentOption(ThemeMode.System, "跟随系统"),
                            SegmentOption(ThemeMode.Light, "浅色"),
                            SegmentOption(ThemeMode.Dark, "深色"),
                        ),
                        selected = settings.themeMode,
                        onSelect = vm::setThemeMode,
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("跟随壁纸配色", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Android 12+。启用后主题色来自壁纸，其余层级保持当前设计。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = settings.dynamicColor,
                            onCheckedChange = vm::setDynamicColor,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SettingsGroup("存储") {
                InfoRow(
                    icon = Icons.Rounded.PhotoLibrary,
                    title = "保存位置",
                    value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        "相册 · Pictures/Video2Gif"
                    } else {
                        "应用图片目录/Video2Gif"
                    },
                    supporting = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        "成品写入系统相册，不覆盖已有文件。"
                    } else {
                        "此系统版本保存在应用目录，可通过分享导出。"
                    },
                )
            }

            Spacer(Modifier.height(20.dp))
            SettingsGroup("处理方式") {
                InfoRow(
                    icon = Icons.Rounded.Lock,
                    title = "只在设备上处理",
                    value = "",
                    supporting = "视频与成品不会离开这台设备，不需要账号。",
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                InfoRow(
                    icon = Icons.Rounded.Bolt,
                    title = "预览阶段不运行 GIF 编码",
                    value = "",
                    supporting = "预览与估算共用减色、抖动和采样定义，但只有正式转换会写入 GIF。",
                )
            }

            Spacer(Modifier.height(20.dp))
            SettingsGroup("关于") {
                Column(Modifier.padding(16.dp)) {
                    Text("Video2Gif", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "版本 $version",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "把手机视频转换成无声 GIF。支持 1–10 个视频的批次、截取、裁剪、旋转、速度、调色板与抖动设置。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            content()
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    supporting: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
