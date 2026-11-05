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
            FileOutputStream(temp).use { output ->
                val encoder = GifEncoder(output, spec.width, spec.height, palette, loopRepeats)
                val pixels = IntArray(spec.width * spec.height)
                for (frame in 0 until spec.frameCount) {
                    coroutineContext.ensureActive()
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
            return EncodeOutcome(temp, temp.length(), spec.width, spec.height, spec.frameCount)
        } catch (c: CancellationException) {
            throw c
        } finally {
            decoder.close()
        }
    }
}

object GifPublisher {

    fun publish(context: Context, tempFile: File, sourceName: String): PublishedGif {
        val displayName = buildDisplayName(sourceName)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishToMediaStore(context, tempFile, displayName)
        } else {
            publishToAppPictures(context, tempFile, displayName)
        }
    }

    private fun buildDisplayName(sourceName: String): String {
        val base = sourceName.substringBeforeLast('.').ifBlank { "video" }
            .replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_")
            .trim()
            .take(48)
            .ifBlank { "video" }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        return "$base-$stamp.gif"
    }

    private fun publishToMediaStore(context: Context, tempFile: File, displayName: String): PublishedGif {
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
            resolver.openOutputStream(uri, "w")?.use { output ->
                tempFile.inputStream().use { input -> input.copyTo(output, 128 * 1024) }
            } ?: throw IOException("无法写入相册")
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            return PublishedGif(
                uri = uri,
                displayName = displayName,
                sizeBytes = tempFile.length(),
                locationLabel = "相册 · Pictures/Video2Gif",
            )
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    @Suppress("DEPRECATION")
    private fun publishToAppPictures(context: Context, tempFile: File, displayName: String): PublishedGif {
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
        tempFile.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output, 128 * 1024) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        return PublishedGif(
            uri = uri,
            displayName = target.name,
            sizeBytes = target.length(),
            locationLabel = "应用图片目录/Video2Gif",
        )
    }

    /** 保存失败时仍可从私有临时文件分享。 */
    fun tempFileUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
