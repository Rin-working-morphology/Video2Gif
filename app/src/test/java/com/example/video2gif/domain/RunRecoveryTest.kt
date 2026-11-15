package com.example.video2gif.domain

import org.junit.Assert.*
import org.junit.Test

class RunRecoveryTest {
    private fun item(status: JobStatus) = RunItem("id", "video", "content://source", null, ConversionRecipe(), status = status)

    @Test fun `timeout preserves successful items and interrupts every active phase`() {
        val items = JobStatus.entries.map(::item)
        val result = RunState("batch", 0, items).interrupted("timeout")
        assertFalse(result.isRunning)
        items.zip(result.items).forEach { (old, new) ->
            if (old.status.isPending) assertEquals(JobStatus.Interrupted, new.status) else assertEquals(old, new)
        }
    }

    @Test fun `cancellation callback after timeout cannot erase interruption or complete temp`() {
        val publishing = item(JobStatus.Publishing).copy(tempPath = "/complete.gif", pendingUri = "content://pending", publishReady = true)
        val interrupted = RunState("batch", 0, listOf(publishing)).interrupted().items.single()
        val canceled = interrupted.canceled(true, "/complete.gif")
        assertEquals(JobStatus.Interrupted, canceled.status)
        assertTrue(canceled.publishOnly)
        assertEquals("/complete.gif", canceled.tempPath)
        assertEquals("content://pending", canceled.pendingUri)
        assertTrue(canceled.publishReady)
    }

    @Test fun `user cancellation discards temp but retains unresolved publication record`() {
        val canceled = item(JobStatus.Publishing).copy(pendingUri = "content://pending").canceled(false, "/complete.gif")
        assertEquals(JobStatus.Canceled, canceled.status)
        assertNull(canceled.tempPath)
        assertFalse(canceled.publishOnly)
        assertEquals("content://pending", canceled.pendingUri)
    }
}
