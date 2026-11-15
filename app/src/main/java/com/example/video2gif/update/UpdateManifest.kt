package com.example.video2gif.update

import java.io.IOException
import java.net.URI
import org.json.JSONException
import org.json.JSONObject

/**
 * 发布渠道常量。仓库改名、迁移组织或换用自建下载站点时，只需要改这里。
 *
 * `latest.json` 由 `.github/workflows/release.yml` 在每次发布时生成并作为 Release
 * 资产上传，所以 `/releases/latest/download/` 这个地址永远指向最近一次正式版本。
 */
object ReleaseChannel {
    const val OWNER = "Rin-working-morphology"
    const val REPO = "Video2Gif"
    const val LATEST_JSON_URL =
        "https://github.com/$OWNER/$REPO/releases/latest/download/latest.json"
    const val RELEASE_PAGE = "https://github.com/$OWNER/$REPO/releases/latest"
}

data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val tag: String,
    val releaseUrl: String,
    val apkUrl: String?,
    val apkSizeBytes: Long?,
    val sha256: String?,
    val notes: String,
) {
    /** 只在远端 versionCode 严格更大时提示更新，禁止把同版本或降级包推给用户。 */
    fun isNewerThan(currentVersionCode: Long): Boolean = versionCode > currentVersionCode
}

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val info: UpdateInfo) : UpdateStatus
    data class Failed(val reason: String) : UpdateStatus
}

object UpdateManifest {
    private const val MAX_NOTES_LENGTH = 600

    /**
     * 解析 latest.json。字段缺失或版本号非法时抛 [IOException]，
     * 由调用方转成“检查失败”，不能把半截数据当成可用更新。
     */
    fun parse(raw: String): UpdateInfo {
        val json = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            throw IOException("版本信息格式不正确")
        }
        val code = json.optLong("versionCode", -1L)
        if (code <= 0L) throw IOException("版本信息缺少有效的 versionCode")
        val tag = json.optString("tag").trim()
        val name = json.optString("versionName").trim().ifBlank { tag.removePrefix("v") }
        if (name.isBlank()) throw IOException("版本信息缺少版本号")
        val notes = json.optString("notes")
            .replace("\r\n", "\n")
            .trim()
            .take(MAX_NOTES_LENGTH)
        return UpdateInfo(
            versionCode = code,
            versionName = name,
            tag = tag,
            releaseUrl = safeReleaseUrl(json.optString("releaseUrl"), tag),
            apkUrl = safeAssetUrl(json.optString("apkUrl")),
            apkSizeBytes = json.optLong("apkSizeBytes", -1L).takeIf { it > 0L },
            sha256 = json.optString("sha256").trim().lowercase()
                .takeIf { it.length == 64 && it.all { c -> c in "0123456789abcdef" } },
            notes = notes,
        )
    }

    private fun safeReleaseUrl(raw: String, tag: String): String {
        val tagUrl = if (tag.isBlank()) {
            ReleaseChannel.RELEASE_PAGE
        } else {
            "https://github.com/${ReleaseChannel.OWNER}/${ReleaseChannel.REPO}/releases/tag/$tag"
        }
        val parsed = runCatching { URI(raw.trim()) }.getOrNull()
        if (parsed == null || parsed.scheme != "https" || parsed.host != "github.com") return tagUrl
        val prefix = "/${ReleaseChannel.OWNER}/${ReleaseChannel.REPO}/"
        return if (parsed.path.orEmpty().startsWith(prefix)) parsed.toString() else tagUrl
    }

    /** APK 链接同样只接受本仓库的 Release 资产，避免 JSON 被篡改后指向任意站点。 */
    private fun safeAssetUrl(raw: String): String? {
        val parsed = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        if (parsed.scheme != "https" || parsed.host != "github.com") return null
        val prefix = "/${ReleaseChannel.OWNER}/${ReleaseChannel.REPO}/releases/download/"
        return if (parsed.path.orEmpty().startsWith(prefix)) parsed.toString() else null
    }
}
