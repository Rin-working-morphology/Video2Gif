package com.example.video2gif.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.video2gif.domain.ConversionRecipe
import com.example.video2gif.domain.LoopMode
import com.example.video2gif.domain.SourceVideo
import com.example.video2gif.domain.gifDelayCentis
import com.example.video2gif.domain.outputSpec
import com.example.video2gif.domain.sourceTimeMsForFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.coroutineContext

data class EncodeOutcome(
    val tempFile: File,
    val byteSize: Long,
    val width: Int,
    val height: Int,
    val frameCount: Int,
)

data class PublishedGif(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val locationLabel: String,
)

/**
 * 正式导出：与预览、估算共用调色板、抖动和采样时间表，只有这里执行 LZW 与容器写入。
 */
class GifConverter(private val context: Context) {

    suspend fun encode(
        uri: Uri,
        source: SourceVideo,
        recipe: ConversionRecipe,
        onProgress: (Float) -> Unit,
    ): EncodeOutcome {
        val spec = recipe.outputSpec(source) ?: throw IOException("无法计算输出尺寸")
        val decoder = FrameDecoder(context, uri)
        try {
            coroutineContext.ensureActive()
            onProgress(0.01f)
            // 冻结调色板：之后所有帧与预览使用同一份颜色表。
            val palette = PaletteBuilder.build(decoder, source, recipe, spec, recipe.colorCount)
            coroutineContext.ensureActive()
            onProgress(0.06f)

            val workDir = File(context.cacheDir, "gif_work").apply { mkdirs() }
            val temp = File(workDir, "v2g_${System.currentTimeMillis()}_${System.nanoTime() % 1000}.gif")
            val loopRepeats = when (recipe.loop) {
                LoopMode.Infinite -> 0
                LoopMode.Once -> -1
                LoopMode.Custom -> (recipe.loopCount - 1).coerceAtLeast(0)
            }
            val requestLong = ImageOps.decodeLongSideForTarget(
                source,
                recipe,
                spec.width,
                spec.height,
                cap = 2560,
            )
            return withTemporaryOutput(temp) {
                FileOutputStream(temp).use { output ->
                    val encoder = GifEncoder(output, spec.width, spec.height, palette, loopRepeats)
                    val pixels = IntArray(spec.width * spec.height)
                    for (frame in 0 until spec.frameCount) {
                        coroutineContext.ensureActive()
                        if (workDir.usableSpace < 1024 * 1024) throw IOException("存储空间不足，无法继续转换")
                        val timeMs = recipe.sourceTimeMsForFrame(source, frame)
                        val decoded = decoder.decode(timeMs, source, requestLong)
                            ?: throw IOException("第 ${frame + 1} 帧无法读取")
                        val prepared = ImageOps.prepare(decoded, recipe, spec.width, spec.height)
                        prepared.getPixels(pixels, 0, spec.width, 0, 0, spec.width, spec.height)
                        prepared.recycle()
                        val indices = mapToIndices(pixels, spec.width, spec.height, palette, recipe.dither)
                        encoder.writeFrame(indices, gifDelayCentis(frame, recipe.fps))
                        val fraction = 0.06f + 0.84f * (frame + 1) / spec.frameCount
                        onProgress(fraction)
                    }
                    encoder.finish()
                }
                onProgress(0.92f)
                EncodeOutcome(temp, temp.length(), spec.width, spec.height, spec.frameCount)
            }
        } catch (c: CancellationException) {
            throw c
        } finally {
            decoder.close()
        }
    }
}

object GifPublisher {

    suspend fun publish(
        context: Context,
        tempFile: File,
        sourceName: String,
        checkActive: () -> Unit = {},
        onTargetName: (String) -> Unit = {},
        checkpoint: (Uri?, Boolean) -> Unit = { _, _ -> },
    ): PublishedGif {
        val displayName = buildDisplayName(sourceName)
        checkActive()
        onTargetName(displayName)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishToMediaStore(context, tempFile, displayName, checkActive, checkpoint)
        } else {
            publishToAppPictures(context, tempFile, displayName, checkActive, checkpoint)
        }
    }

    private fun buildDisplayName(sourceName: String): String {
        val base = sourceName.substringBeforeLast('.').ifBlank { "video" }
            .replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_")
            .trim()
            .take(48)
            .ifBlank { "video" }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        return "$base-$stamp-${UUID.randomUUID().toString().take(8)}.gif"
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun publishToMediaStore(context: Context, tempFile: File, displayName: String,
        checkActive: () -> Unit, checkpoint: (Uri?, Boolean) -> Unit): PublishedGif {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Video2Gif")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("无法创建相册条目")
        try {
            checkpoint(uri, false)
            resolver.openOutputStream(uri, "w")?.use { output ->
                tempFile.inputStream().use { input ->
                    copyExport(input, output, tempFile.length()) {
                        checkActive()
                        Environment.getExternalStorageDirectory().usableSpace
                    }
                }
            } ?: throw IOException("无法写入相册")
            checkActive()
            checkpoint(uri, true)
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            if (resolver.update(uri, done, null, null) != 1) throw IOException("相册发布失败，请重试保存")
            return PublishedGif(
                uri = uri,
                displayName = displayName,
                sizeBytes = tempFile.length(),
                locationLabel = "相册 · Pictures/Video2Gif",
            )
        } catch (t: Throwable) {
            val deleted = runCatching { resolver.delete(uri, null, null) > 0 }.getOrDefault(false)
            if (deleted) checkpoint(null, false)
            throw t
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun publishToAppPictures(context: Context, tempFile: File, displayName: String,
        checkActive: () -> Unit, checkpoint: (Uri?, Boolean) -> Unit): PublishedGif {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
            "Video2Gif",
        ).apply { mkdirs() }
        var target = File(dir, displayName)
        var index = 1
        while (target.exists()) {
            target = File(dir, displayName.replace(".gif", "-$index.gif"))
            index++
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        try {
            checkpoint(uri, false)
            tempFile.inputStream().use { input ->
                target.outputStream().use { output ->
                    copyExport(input, output, tempFile.length()) {
                        checkActive()
                        dir.usableSpace
                    }
                }
            }
            checkActive()
            checkpoint(uri, true)
        } catch (t: Throwable) {
            if (!target.exists() || target.delete()) checkpoint(null, false)
            throw t
        }
        return PublishedGif(
            uri = uri,
            displayName = target.name,
            sizeBytes = target.length(),
            locationLabel = "应用图片目录/Video2Gif",
        )
    }

    /** 启动时对账：ready 表示复制及关闭已完成；未完成目标先删除，失败时保留 URI。 */
    fun recover(context: Context, item: com.example.video2gif.domain.RunItem): com.example.video2gif.domain.RunItem {
        // 文件名先于 insert 持久化，覆盖 insert 成功但 URI 尚未写入快照的退出窗口。
        if (item.pendingUri == null && item.outputUri == null && item.outputName != null && Build.VERSION.SDK_INT >= 29) {
            try {
                val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val found = context.contentResolver.query(collection, arrayOf(MediaStore.Images.Media._ID),
                    "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ?",
                    arrayOf(item.outputName, "Pictures/Video2Gif/"), null)?.use {
                    if (it.moveToFirst()) android.content.ContentUris.withAppendedId(collection, it.getLong(0)) else null
                }
                if (found != null) return recover(context, item.copy(pendingUri = found.toString()))
            } catch (e: Exception) {
                throw IOException("无法核对上次保存的文件，请重试", e)
            }
        }
        val pending = item.pendingUri ?: return item
        val uri = Uri.parse(pending)
        val resolver = context.contentResolver
        return try {
            if (item.publishReady) {
                val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                    ?: throw IOException("保存的文件已不可用")
                if (item.outputSizeBytes > 0 && size != item.outputSizeBytes) throw IOException("保存的文件不完整")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                    if (resolver.update(uri, values, null, null) != 1) throw IOException("无法完成相册发布")
                }
                val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
                item.copy(status = com.example.video2gif.domain.JobStatus.Succeeded, progress = 1f,
                    outputUri = pending, pendingUri = null, publishReady = false, publishOnly = false,
                    error = null, outputSizeBytes = size, outputName = name ?: item.outputName,
                    locationLabel = if (Build.VERSION.SDK_INT >= 29) "相册 · Pictures/Video2Gif" else "应用图片目录/Video2Gif")
            } else {
                resolver.delete(uri, null, null)
                // 删除返回 0 也可能是已不存在；读取仍成功则不能丢弃清理记录。
                val remains = runCatching { resolver.openAssetFileDescriptor(uri, "r")?.use { true } == true }.getOrDefault(false)
                if (remains) throw IOException("未完成文件暂时无法清理")
                item.copy(pendingUri = null, publishReady = false)
            }
        } catch (e: Exception) {
            item.copy(status = com.example.video2gif.domain.JobStatus.Failed,
                error = "保存状态待恢复：${e.message}")
        }
    }

    /** 保存失败时仍可从私有临时文件分享。 */
    fun tempFileUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
