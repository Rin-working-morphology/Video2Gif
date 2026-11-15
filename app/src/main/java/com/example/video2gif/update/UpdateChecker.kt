package com.example.video2gif.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 检查 GitHub Release 上的 `latest.json`。
 *
 * 只发起一次 HTTPS GET，响应上限 64 KiB、8 秒超时；不跟随非 HTTPS 跳转，
 * 也不把任何响应内容当指令执行。
 */
object UpdateChecker {
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000
    private const val MAX_RESPONSE_BYTES = 64 * 1024

    @Throws(IOException::class)
    fun fetch(url: String = ReleaseChannel.LATEST_JSON_URL): UpdateInfo {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Video2Gif-Android")
        }
        try {
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                throw IOException("服务器返回 $status")
            }
            return UpdateManifest.parse(connection.inputStream.use(::readLimited))
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_RESPONSE_BYTES) throw IOException("版本信息响应过大")
            output.write(buffer, 0, read)
        }
        return output.toString("UTF-8")
    }

    /** 网络错误转成用户能理解的一句话，原始异常细节不直接暴露。 */
    fun userFacingError(error: Throwable): String = when (error) {
        is UnknownHostException -> "网络不可用，请检查连接后重试"
        is SocketTimeoutException -> "连接超时，请稍后重试"
        is SSLException -> "安全连接失败，请稍后重试"
        is IOException -> error.message?.takeIf { it.isNotBlank() } ?: "网络请求失败，请稍后重试"
        else -> "检查更新失败，请稍后重试"
    }

    fun currentVersionCode(context: Context): Long {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    fun currentVersionName(context: Context): String = runCatching {
        @Suppress("DEPRECATION")
        val info: android.content.pm.PackageInfo =
            context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "1.0"

    /** 部分厂商 ROM 需要显式判断才能区分“没装浏览器”和“用户取消”。 */
    fun canOpenUrl(context: Context, url: String): Boolean = runCatching {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY,
        ).isNotEmpty()
    }.getOrDefault(false)
}
