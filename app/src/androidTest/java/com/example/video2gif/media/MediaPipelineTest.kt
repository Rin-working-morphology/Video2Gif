package com.example.video2gif.media

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.video2gif.domain.*
import com.example.video2gif.data.RunStore
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 小型设备回归；测试创建的来源、任务记录和相册条目均在 finally 清理。 */
@RunWith(AndroidJUnit4::class)
class MediaPipelineTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    // 使用测试 APK 的私有目录和 preferences，避免覆盖用户的任务。
    private val context = instrumentation.context

    private suspend fun withVideo(block: suspend (File, SourceVideo) -> Unit) {
        val video = File.createTempFile("codec-test", ".mp4", context.cacheDir)
        try {
            context.assets.open("codec-sdr.mp4").use { input -> video.outputStream().use { input.copyTo(it) } }
            val source = VideoProbe.probe(context, Uri.fromFile(video))!!
            block(video, source)
        } finally { video.delete() }
    }

    @Test fun sequentialSeekAndRotation() = runBlocking {
        withVideo { file, source ->
            FrameDecoder(context, Uri.fromFile(file)).use { decoder ->
                val first = decoder.decode(0, source, 96)!!
                for (time in listOf(83L, 167L, 500L, 900L)) {
                    decoder.decode(time, source, 96)!!.also {
                        assertEquals(96, it.width)
                        assertEquals(64, it.height)
                        it.recycle()
                    }
                }
                val rewind = decoder.decode(0, source, 96)!!
                assertTrue(first.sameAs(rewind))
                first.recycle(); rewind.recycle()
            }
            FrameDecoder(context, Uri.fromFile(file)).use { decoder ->
                decoder.decode(0, source.copy(rotationDegrees = 90), 96)!!.also {
                    assertEquals(64, it.width); assertEquals(96, it.height); it.recycle()
                }
            }
        }
    }

    @Test fun cancellationRemovesPartialGif() = runBlocking {
        withVideo { file, source ->
            val job = Job()
            val dir = File(context.cacheDir, "gif_work")
            val before = dir.listFiles()?.toSet().orEmpty()
            try {
                withContext(job) {
                    GifConverter(context).encode(Uri.fromFile(file), source,
                        ConversionRecipe(trimEndMs = 1000, longSide = 96)) { if (it > 0.15f) job.cancel() }
                }
                fail("应取消")
            } catch (_: CancellationException) { }
            assertEquals(before, dir.listFiles()?.toSet().orEmpty())
        }
    }

    @Test fun persistedPublishingCheckpointSurvivesStoreRecreation() {
        val store = RunStore(context)
        try {
            val item = RunItem("test", "test.mp4", "content://source", null, ConversionRecipe(),
                status = JobStatus.Publishing, tempPath = "/complete.gif", publishOnly = true,
                pendingUri = "content://pending", publishReady = true)
            store.save(RunState("test", 1, listOf(item)))
            val restored = RunStore(context).load()!!.items.single()
            assertEquals(JobStatus.Interrupted, restored.status)
            assertEquals(item.pendingUri, restored.pendingUri)
            assertTrue(restored.publishReady)
            assertTrue(restored.publishOnly)
        } finally { store.clear() }
    }
}
