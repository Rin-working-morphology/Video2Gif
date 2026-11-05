package com.example.video2gif.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 用户发起转换后运行的前台服务。任务状态由 [ConversionEngine] 持有；
 * 服务只负责前台通知、取消入口与生命周期，不重复拥有队列。
 */
class ConversionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stopping = false

    override fun onCreate() {
        super.onCreate()
        ConversionNotifications.ensureChannel(this)
        ConversionEngine.startContext(this)
        startForegroundCompat(ConversionEngine.state.value)
        scope.launch {
            ConversionEngine.state.collect { state ->
                val manager = getSystemService(NotificationManager::class.java)
                if (state == null) {
                    stopSelfSafely()
                    return@collect
                }
                manager?.notify(
                    ConversionNotifications.NOTIFICATION_ID,
                    ConversionNotifications.build(this@ConversionService, state),
                )
                if (!state.isRunning) {
                    stopSelfSafely()
                }
            }
        }
        ConversionEngine.execute(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_ALL -> ConversionEngine.cancelAll()
            ACTION_CANCEL_CURRENT -> ConversionEngine.cancelCurrent()
        }
        if (ConversionEngine.state.value == null) {
            stopSelfSafely()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(state: com.example.video2gif.domain.RunState?) {
        val notification = ConversionNotifications.build(this, state)
        // androidx.core 1.18 的 ServiceCompat 会过滤掉 API 35 才加入的 mediaProcessing 位，
        // 因此这里按平台版本直接调用，避免类型被掩码成 NONE。
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> startForeground(
                ConversionNotifications.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> startForeground(
                ConversionNotifications.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
            else -> startForeground(ConversionNotifications.NOTIFICATION_ID, notification)
        }
    }

    private fun stopSelfSafely() {
        if (stopping) return
        stopping = true
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Android 15+ 媒体处理前台服务有运行时长预算；到达限制时保存明确的中断状态，
     * 由用户重试，不能悄悄把未完成项当作成功。
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        ConversionEngine.onServiceTimeout()
        stopSelfSafely()
    }

    companion object {
        const val ACTION_START = "com.example.video2gif.action.START"
        const val ACTION_CANCEL_ALL = "com.example.video2gif.action.CANCEL_ALL"
        const val ACTION_CANCEL_CURRENT = "com.example.video2gif.action.CANCEL_CURRENT"

        fun start(context: Context) {
            val intent = Intent(context, ConversionService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancelAll(context: Context) {
            context.startService(
                Intent(context, ConversionService::class.java).setAction(ACTION_CANCEL_ALL),
            )
        }
    }
}
