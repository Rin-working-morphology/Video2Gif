package com.example.video2gif.data

import com.example.video2gif.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class JsonMigrationTest {
    private val source = SourceVideo(1920, 1080, 90, 5000, "video/avc", 30f)
    private fun item(status: JobStatus) = RunItem(status.name, "test.mp4", "content://video", source,
        ConversionRecipe(trimStartMs = 200, trimEndMs = 4000), status = status)

    @Test fun `legacy snapshot migrates without losing recipes or successful outputs`() {
        val succeeded = item(JobStatus.Succeeded).copy(outputUri = "content://result", outputSizeBytes = 1234)
        val root = JSONObject(JsonCodec.runToJson(RunState("batch", 123, listOf(succeeded, item(JobStatus.Encoding)))))
        root.remove("schemaVersion")
        val loaded = JsonCodec.runFromJson(root.toString())!!
        assertEquals(succeeded, loaded.items[0])
        assertEquals(JobStatus.Interrupted, loaded.items[1].status)
        assertEquals(source, loaded.items[1].source)
        assertEquals(200L, loaded.items[1].recipe.trimStartMs)
        assertFalse(loaded.isRunning)
    }

    @Test fun `process death at every publication checkpoint preserves recovery information`() {
        for (ready in listOf(false, true)) {
            val publishing = item(JobStatus.Publishing).copy(tempPath = "/complete.gif", publishOnly = true,
                pendingUri = "content://pending", publishReady = ready, outputSizeBytes = 1000)
            // isRunning=false 也不能使残留活动状态永远卡住。
            val loaded = JsonCodec.runFromJson(JsonCodec.runToJson(RunState("b", 1, listOf(publishing), isRunning = false)))!!
            val restored = loaded.items.single()
            assertEquals(JobStatus.Interrupted, restored.status)
            assertEquals(publishing.pendingUri, restored.pendingUri)
            assertEquals(ready, restored.publishReady)
            assertTrue(restored.publishOnly)
            assertEquals("/complete.gif", restored.tempPath)
            assertEquals(1000L, restored.outputSizeBytes)
        }
    }

    @Test fun `future and malformed snapshots report error instead of silently disappearing`() {
        for (raw in listOf("{broken", "{\"schemaVersion\":999,\"items\":[]}")) {
            try { JsonCodec.runFromJson(raw); fail("应报告版本或数据错误") }
            catch (_: IOException) { }
        }
    }
}
