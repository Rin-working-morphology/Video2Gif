package com.example.video2gif

import android.app.Application
import com.example.video2gif.service.ConversionEngine
import com.example.video2gif.service.ConversionNotifications
import java.io.File

class Video2GifApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ConversionNotifications.ensureChannel(this)
        // 恢复最近任务快照；未完成项在解码器中标记为 Interrupted。
        ConversionEngine.reconcile(this)
        cleanupOrphanTemps()
    }

    /** 清理没有被任何任务引用的私有临时 GIF，避免进程中断后残留。 */
    private fun cleanupOrphanTemps() {
        val referenced = ConversionEngine.state.value?.items
            ?.mapNotNull { it.tempPath }
            ?.toHashSet()
            .orEmpty()
        val dir = File(cacheDir, "gif_work")
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { file ->
            if (file.absolutePath !in referenced && now - file.lastModified() > 24 * 60 * 60 * 1000L) {
                file.delete()
            }
        }
    }
}
