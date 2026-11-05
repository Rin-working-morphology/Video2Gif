package com.example.video2gif.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.video2gif.domain.LoopMode
import com.example.video2gif.domain.formatDuration
import com.example.video2gif.domain.formatTimecode
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> OptionSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    description: String? = null,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (description != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clickable { onSelect(value) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "已选择",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SizeSheet(
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var customOpen by remember { mutableStateOf(false) }
    val presets = listOf(240, 360, 480, 720)
    val isCustom = selected !in presets
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text("输出尺寸", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "长边像素。保持裁剪后的比例，默认不放大；上限 1080。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            SizeOptionRow("长边 240", "更省空间", 240, selected) { onSelect(it) }
            SizeOptionRow("长边 360", "小图与表情包", 360, selected) { onSelect(it) }
            SizeOptionRow("长边 480", "默认，适合聊天分享", 480, selected) { onSelect(it) }
            SizeOptionRow("长边 720", "更清晰，体积更大", 720, selected) { onSelect(it) }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clickable { customOpen = true },
                shape = RoundedCornerShape(12.dp),
                color = if (isCustom) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "自定义",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        color = if (isCustom) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (isCustom) "长边 $selected" else "64–1080",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (customOpen) {
        NumberInputDialog(
            title = "自定义长边",
            initial = selected,
            range = 64..1080,
            suffix = "px",
            onConfirm = {
                customOpen = false
                onSelect(it)
            },
            onDismiss = { customOpen = false },
        )
    }
}

@Composable
private fun SizeOptionRow(
    label: String,
    supporting: String,
    value: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val isSelected = value == selected
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onSelect(value) },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
fun NumberInputDialog(
    title: String,
    initial: Int,
    range: IntRange,
    suffix: String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in range
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text("数值") },
                    suffix = { Text(suffix) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !valid,
                    supportingText = {
                        Text("范围 ${range.first}–${range.last} $suffix")
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = valid) {
                Text("应用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoopSheet(
    loop: LoopMode,
    count: Int,
    onConfirm: (LoopMode, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(loop) }
    var plays by remember { mutableStateOf(count.coerceIn(1, 20)) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text("循环次数", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "GIF 循环由播放器解释，部分应用会忽略有限次数。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            LoopRow("无限循环", LoopMode.Infinite, mode) { mode = it }
            LoopRow("播放一次", LoopMode.Once, mode) { mode = it }
            LoopRow("自定义次数", LoopMode.Custom, mode) { mode = it }
            if (mode == LoopMode.Custom) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("播放次数", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { plays = (plays - 1).coerceAtLeast(1) },
                        enabled = plays > 1,
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "减少次数")
                    }
                    Text(
                        "$plays",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(
                        onClick = { plays = (plays + 1).coerceAtMost(20) },
                        enabled = plays < 20,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "增加次数")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { onConfirm(mode, plays) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("应用")
            }
        }
    }
}

@Composable
private fun LoopRow(label: String, value: LoopMode, selected: LoopMode, onSelect: (LoopMode) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onSelect(value) },
        shape = RoundedCornerShape(12.dp),
        color = if (selected == value) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected == value) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (selected == value) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
fun TrimTimeDialog(
    startMs: Long,
    endMs: Long,
    durationMs: Long,
    playheadMs: Long,
    onConfirm: (Long, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var startText by remember { mutableStateOf(formatTimecode(startMs)) }
    var endText by remember { mutableStateOf(formatTimecode(endMs)) }
    val start = parseTimeInput(startText)
    val end = parseTimeInput(endText)
    val valid = start != null && end != null && start >= 0 && end <= durationMs && end - start >= 200
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("截取时间") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "可输入 秒（如 3.20）或 分:秒.厘秒（00:03.20）。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = { Text("起点") },
                    singleLine = true,
                    isError = start == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    label = { Text("终点") },
                    singleLine = true,
                    isError = end == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            startText = formatTimecode(playheadMs.coerceAtMost(endMs - 200L))
                        },
                    ) { Text("起点设为当前帧") }
                    TextButton(
                        onClick = {
                            endText = formatTimecode(playheadMs.coerceAtLeast(startMs + 200L))
                        },
                    ) { Text("终点设为当前帧") }
                }
                if (!valid) {
                    Text(
                        "终点需比起点晚至少 0.2 秒，且不超过视频时长。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        "截取 ${formatDuration(end!! - start!!)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (valid && start != null && end != null) onConfirm(start, end)
                },
                enabled = valid,
            ) { Text("应用") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 支持纯秒数与 mm:ss.cc / h:mm:ss.cc。 */
fun parseTimeInput(input: String): Long? {
    val text = input.trim().replace('：', ':')
    if (text.isEmpty()) return null
    return try {
        if (text.contains(':')) {
            val parts = text.split(':')
            if (parts.size !in 2..3) return null
            var total = 0.0
            val weights = if (parts.size == 2) listOf(60.0, 1.0) else listOf(3600.0, 60.0, 1.0)
            parts.forEachIndexed { index, part ->
                val value = part.toDoubleOrNull() ?: return null
                total += value * weights[index]
            }
            (total * 1000).roundToLong()
        } else {
            (text.toDoubleOrNull() ?: return null).let { (it * 1000).roundToLong() }
        }
    } catch (_: Exception) {
        null
    }
}
