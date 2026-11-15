package com.example.video2gif.media

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.example.video2gif.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class PublicationRecoveryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver

    @Test fun failedSaveDeletesPendingAndAllowsPublishOnlyRetry() = runBlocking {
        val temp = completeGif()
        var created: Uri? = null
        var result: Uri? = null
        try {
            try {
                GifPublisher.publish(context, temp, "v2g-test") { uri, ready ->
                    if (uri != null) created = uri
                    if (ready) throw IOException("注入公开前保存失败")
                }
                fail("应失败")
            } catch (_: IOException) { }
            assertTrue(temp.exists())
            assertFalse(exists(created!!))
            result = GifPublisher.publish(context, temp, "v2g-test").uri
            assertTrue(exists(result))
        } finally {
            created?.let { resolver.delete(it, null, null) }
            result?.let { resolver.delete(it, null, null) }
            temp.delete()
        }
    }

    @Test fun restartAtCopyAndVisibilityWindowsDoesNotDuplicateResults() {
        val temp = completeGif()
        try {
            for (ready in listOf(false, true)) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "v2g-recovery-${System.nanoTime()}.gif")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Video2Gif")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
                try {
                    resolver.openOutputStream(uri)!!.use { out -> temp.inputStream().use { it.copyTo(out) } }
                    val item = RunItem("test", "test.mp4", "content://source", null, ConversionRecipe(),
                        status = JobStatus.Interrupted, tempPath = temp.absolutePath, publishOnly = true,
                        pendingUri = uri.toString(), publishReady = ready, outputSizeBytes = temp.length())
                    val recovered = GifPublisher.recover(context, item)
                    if (ready) {
                        assertEquals(JobStatus.Succeeded, recovered.status)
                        assertEquals(uri.toString(), recovered.outputUri)
                        assertEquals(recovered, GifPublisher.recover(context, recovered))
                        resolver.query(uri, arrayOf(MediaStore.Images.Media.IS_PENDING), null, null, null)!!.use {
                            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
                        }
                    } else {
                        assertNull(recovered.pendingUri)
                        assertFalse(exists(uri))
                        assertTrue(temp.exists())
                    }
                } finally { resolver.delete(uri, null, null) }
            }
        } finally { temp.delete() }
    }

    private fun completeGif(): File = File.createTempFile("v2g-publication", ".gif", context.cacheDir).also { file ->
        file.outputStream().use {
            GifEncoder(it, 2, 2, ColorPalette(intArrayOf(0, 0xFFFFFF)), 0).apply {
                writeFrame(byteArrayOf(0, 1, 1, 0), 10)
                finish()
            }
        }
    }

    private fun exists(uri: Uri): Boolean = resolver.query(uri, arrayOf(MediaStore.Images.Media._ID), null, null, null)?.use {
        it.moveToFirst()
    } == true
}
