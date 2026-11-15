package com.example.video2gif.update

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {

    private val validJson = """
        {
          "versionCode": 30512345,
          "versionName": "1.2.0",
          "tag": "v1.2.0",
          "releaseUrl": "https://github.com/Rin-working-morphology/Video2Gif/releases/tag/v1.2.0",
          "apkUrl": "https://github.com/Rin-working-morphology/Video2Gif/releases/download/v1.2.0/Video2Gif-1.2.0.apk",
          "apkSizeBytes": 4123456,
          "sha256": "ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789",
          "notes": "修复部分设备的颜色偏差\n新增批量导出"
        }
    """.trimIndent()

    @Test
    fun parsesValidManifest() {
        val info = UpdateManifest.parse(validJson)
        assertEquals(30512345L, info.versionCode)
        assertEquals("1.2.0", info.versionName)
        assertEquals("v1.2.0", info.tag)
        assertEquals(
            "https://github.com/Rin-working-morphology/Video2Gif/releases/tag/v1.2.0",
            info.releaseUrl,
        )
        assertEquals(4123456L, info.apkSizeBytes)
        assertTrue(info.sha256 == "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789")
        assertEquals("修复部分设备的颜色偏差\n新增批量导出", info.notes)
    }

    @Test
    fun rejectsMissingVersionCode() {
        val raw = """{"versionName":"1.1.0","tag":"v1.1.0"}"""
        assertThrows(IOException::class.java) { UpdateManifest.parse(raw) }
    }

    @Test
    fun rejectsNonJsonPayload() {
        assertThrows(IOException::class.java) { UpdateManifest.parse("<html>404</html>") }
    }

    @Test
    fun fallsBackWhenReleaseUrlPointsElsewhere() {
        val raw = validJson.replace(
            "https://github.com/Rin-working-morphology/Video2Gif/releases/tag/v1.2.0",
            "https://evil.example.com/download",
        )
        val info = UpdateManifest.parse(raw)
        assertEquals(
            "https://github.com/Rin-working-morphology/Video2Gif/releases/tag/v1.2.0",
            info.releaseUrl,
        )
    }

    @Test
    fun dropsUntrustedApkUrlAndInvalidSha() {
        val raw = validJson.replace(
            "https://github.com/Rin-working-morphology/Video2Gif/releases/download/v1.2.0/Video2Gif-1.2.0.apk",
            "https://evil.example.com/Video2Gif.apk",
        ).replace(
            "ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789",
            "not-a-sha",
        )
        val info = UpdateManifest.parse(raw)
        assertNull(info.apkUrl)
        assertNull(info.sha256)
    }

    @Test
    fun derivesVersionNameFromTag() {
        val raw = """{"versionCode":42,"tag":"v2.0.0"}"""
        assertEquals("2.0.0", UpdateManifest.parse(raw).versionName)
    }

    @Test
    fun onlyStrictlyNewerVersionCodeCounts() {
        val info = UpdateManifest.parse(validJson)
        assertTrue(info.isNewerThan(info.versionCode - 1))
        assertFalse(info.isNewerThan(info.versionCode))
        assertFalse(info.isNewerThan(info.versionCode + 1))
    }

    @Test
    fun trimsOverlongNotes() {
        val notes = "a".repeat(5000)
        val raw = """{"versionCode":7,"versionName":"1.0.7","notes":"$notes"}"""
        assertEquals(600, UpdateManifest.parse(raw).notes.length)
    }
}
