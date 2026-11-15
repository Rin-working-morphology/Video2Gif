package com.example.video2gif.media

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files

class ExportFaultTest {
    @Test fun `cancel in copy stops before next chunk`() = runBlocking {
        val job = Job()
        val output = object : ByteArrayOutputStream() {
            override fun write(b: ByteArray, off: Int, len: Int) {
                super.write(b, off, len)
                job.cancel()
            }
        }
        try {
            withContext(job) { copyExport(ByteArrayInputStream(ByteArray(400_000)), output, 400_000) { Long.MAX_VALUE } }
            fail("应响应取消")
        } catch (_: CancellationException) { }
        assertEquals(128 * 1024, output.size())
    }

    @Test fun `disk runs out during copy`() = runBlocking {
        val output = ByteArrayOutputStream()
        try {
            copyExport(ByteArrayInputStream(ByteArray(400_000)), output, 400_000) {
                if (output.size() == 0) Long.MAX_VALUE else 0
            }
            fail("应报告空间不足")
        } catch (e: IOException) { assertTrue(e.message!!.contains("空间不足")) }
        assertEquals(128 * 1024, output.size())
    }

    @Test fun `partial encoder file is removed on failure and cancellation`() = runBlocking {
        for (failure in listOf(IOException("ENOSPC"), CancellationException("cancel"))) {
            val file = Files.createTempFile("v2g-fault", ".gif").toFile()
            try {
                withTemporaryOutput(file) { file.writeText("GIF89a"); throw failure }
            } catch (e: Exception) { assertSame(failure, e) }
            assertFalse(file.exists())
        }
    }

    @Test fun `save failure retains complete source for retry`() = runBlocking {
        val file = Files.createTempFile("v2g-complete", ".gif").toFile()
        try {
            file.writeBytes(ByteArray(200_000) { 42 })
            val broken = object : OutputStream() { override fun write(b: Int) { throw IOException("provider failure") } }
            try {
                file.inputStream().use { copyExport(it, broken, file.length()) { Long.MAX_VALUE } }
                fail("应报告保存失败")
            } catch (_: IOException) { }
            assertEquals(200_000L, file.length())
            val retry = ByteArrayOutputStream()
            file.inputStream().use { copyExport(it, retry, file.length()) { Long.MAX_VALUE } }
            assertArrayEquals(file.readBytes(), retry.toByteArray())
        } finally { file.delete() }
    }

    @Test fun `truncated source cannot become success`() = runBlocking {
        try {
            copyExport(ByteArrayInputStream(ByteArray(4)), ByteArrayOutputStream(), 8) { Long.MAX_VALUE }
            fail("应报告不完整复制")
        } catch (_: IOException) { }
    }
}
