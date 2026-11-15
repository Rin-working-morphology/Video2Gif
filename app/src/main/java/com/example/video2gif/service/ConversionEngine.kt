package com.example.video2gif.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.video2gif.data.RunStore
import com.example.video2gif.domain.BatchDraft
import com.example.video2gif.domain.JobStatus
import com.example.video2gif.domain.RunItem
import com.example.video2gif.domain.RunState
import com.example.video2gif.domain.outputSpec
import com.example.video2gif.domain.interrupted
import com.example.video2gif.domain.canceled
import com.example.video2gif.media.GifConverter
import com.example.video2gif.media.GifPublisher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 串行转换引擎。界面只观察 [state]；编码器只在任务引擎内被创建，
 * 预览与估算没有任何进入编码的路径。
 */
object ConversionEngine {
    private const val TAG = "Video2Gif"
    private const val MIN_FREE_BYTES = 64L * 1024 * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<RunState?>(null)
    val state: StateFlow<RunState?> = _state.asStateFlow()

    private var runJob: Job? = null
    private var activeItemJob: Job? = null
    @Volatile private var cancelAllRequested = false
    @Volatile private var timedOut = false
    private var runStore: RunStore? = null

    private fun store(context: Context): RunStore =
        runStore ?: RunStore(context.applicationContext).also { runStore = it }

    fun begin(context: Context, batch: BatchDraft): Boolean {
        appContext = context.applicationContext
        synchronized(this) {
            if (runJob?.isActive == true) return false
            if (_state.value?.isRunning == true) return false
            val items = batch.items.mapNotNull { item ->
                val source = item.source ?: return@mapNotNull null
                if (item.error != null) return@mapNotNull null
                val recipe = if (item.recipe.trimEndMs <= 0L) {
                    item.recipe.copy(trimEndMs = source.durationMs)
                } else {
                    item.recipe
                }
                RunItem(
                    id = item.id,
                    name = item.name,
                    uri = item.uri,
                    source = source,
                    recipe = recipe,
                )
            }
            if (items.isEmpty()) return false
            val run = RunState(
                batchId = batch.id,
                startedAt = System.currentTimeMillis(),
                items = items,
                currentIndex = -1,
                isRunning = true,
            )
            cancelAllRequested = false
            timedOut = false
            _state.value = run
            if (!saveBeforeStart(context)) return false
            return startService(context)
        }
    }

    internal fun execute(context: Context) {
        appContext = context.applicationContext
        val current = _state.value ?: return
        if (!current.isRunning) return
        synchronized(this) {
            if (runJob?.isActive == true) return
            runJob = scope.launch { process(context.applicationContext) }
        }
    }

    private suspend fun process(context: Context) {
        var canceledAll = false
        try {
            while (true) {
                val snapshot = _state.value ?: break
                if (!snapshot.isRunning) break
                if (cancelAllRequested) {
                    canceledAll = true
                    break
                }
                val index = snapshot.items.indexOfFirst { it.status == JobStatus.Queued }
                if (index < 0) break
                if (freeBytes(context) < MIN_FREE_BYTES) {
                    failRemaining(context, "存储空间不足，暂时无法转换")
                    break
                }
                updateItem(index) {
                    it.copy(status = JobStatus.Preparing, progress = 0.01f, error = null)
                }
                _state.update { it?.copy(currentIndex = index) }
                persist(context)
                val itemJob = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) { processItem(context, index) }
                synchronized(this) {
                    activeItemJob = itemJob
                    if (cancelAllRequested || timedOut) itemJob.cancel() else itemJob.start()
                }
                itemJob.join()
                activeItemJob = null
                // LAZY 子任务在开始前就被取消时，不会进入 processItem 的 catch。
                if (itemJob.isCancelled) {
                    updateItem(index) { item ->
                        if (item.status == JobStatus.Preparing) item.canceled(timedOut, null) else item
                    }
                    persist(context)
                }
                if (cancelAllRequested) {
                    canceledAll = true
                    break
                }
            }
        } catch (e: Exception) {
            _state.update { it?.interrupted("任务已停止：${e.message ?: "无法保存任务状态"}") }
        } finally {
            activeItemJob = null
            if (canceledAll || cancelAllRequested) {
                runCatching { markRemainingCanceled(context) }
            }
            cancelAllRequested = false
            val finalState = _state.value?.let { run ->
                if (!run.isRunning) run
                else run.copy(
                    isRunning = false,
                    currentIndex = -1,
                    stopReason = when {
                        canceledAll -> "已取消"
                        else -> null
                    },
                )
            }
            if (finalState != null) {
                _state.value = finalState
                runCatching { persist(context) }
            }
            runJob = null
        }
    }

    private suspend fun processItem(context: Context, index: Int) {
        val item = _state.value?.items?.getOrNull(index) ?: return
        val source = item.source ?: run {
            failItem(context, index, "来源信息缺失，请重新选择视频")
            return
        }
        if (item.recipe.outputSpec(source) == null) {
            failItem(context, index, "无法计算输出尺寸")
            return
        }
        if (item.pendingUri != null || item.outputName != null) {
            val recovered = try { GifPublisher.recover(context, item) } catch (e: Exception) {
                item.copy(status = JobStatus.Failed, error = e.message)
            }
            updateItem(index) { recovered }
            persist(context)
            if (recovered.status == JobStatus.Succeeded || recovered.status == JobStatus.Failed || recovered.pendingUri != null) return
        }
        val converter = GifConverter(context)
        var tempFile: File? = item.tempPath?.let(::File)?.takeIf { it.exists() && it.length() > 0 }
        var encoded = tempFile != null && item.publishOnly
        try {
            if (!encoded) {
                updateItem(index) { it.copy(status = JobStatus.Encoding, progress = 0.03f) }
                val outcome = converter.encode(
                    uri = Uri.parse(item.uri),
                    source = source,
                    recipe = item.recipe,
                ) { progress ->
                    updateProgress(index, 0.05f + progress * 0.86f)
                }
                tempFile = outcome.tempFile
                encoded = true
                updateItem(index) {
                    it.copy(
                        status = JobStatus.Publishing,
                        progress = 0.94f,
                        tempPath = outcome.tempFile.absolutePath,
                        publishOnly = true,
                        outputSizeBytes = outcome.byteSize,
                    )
                }
                persist(context)
            } else {
                updateItem(index) { it.copy(status = JobStatus.Publishing, progress = 0.94f) }
            }

            val file = tempFile ?: error("临时文件缺失")
            persist(context)
            val task = coroutineContext
            // IO 完成后必须把结果交回状态机，避免取消恰好吞掉已发布的 URI。
            withContext(NonCancellable) {
                val published = GifPublisher.publish(context, file, item.name, checkActive = { task.ensureActive() },
                    onTargetName = { name ->
                        updateItem(index) { it.copy(outputName = name) }
                        persist(context)
                    }) { uri, ready ->
                        updateItem(index) { it.copy(pendingUri = uri?.toString(), publishReady = ready) }
                        persist(context)
                    }
                updateItem(index) {
                    it.copy(
                        status = JobStatus.Succeeded,
                        progress = 1f,
                        error = null,
                        tempPath = null,
                        outputUri = published.uri.toString(),
                        outputName = published.displayName,
                        outputSizeBytes = published.sizeBytes,
                        locationLabel = published.locationLabel,
                        publishOnly = false,
                        pendingUri = null,
                        publishReady = false,
                    )
                }
                persist(context)
                runCatching { file.delete() }
            }
        } catch (cancellation: CancellationException) {
            if (_state.value?.items?.getOrNull(index)?.status == JobStatus.Succeeded) return
            updateItem(index) {
                it.canceled(timedOut, if (encoded) tempFile?.absolutePath else null)
            }
            if (!timedOut) runCatching { tempFile?.delete() }
            runCatching { persist(context) }
            throw cancellation
        } catch (t: Throwable) {
            Log.w(TAG, "convert failed", t)
            val message = t.message?.takeIf { it.isNotBlank() } ?: "转换失败"
            if (encoded && tempFile != null && tempFile.exists()) {
                updateItem(index) {
                    it.copy(
                        status = JobStatus.Failed,
                        progress = 0.94f,
                        error = "转换完成，保存失败：$message",
                        tempPath = tempFile.absolutePath,
                        publishOnly = true,
                    )
                }
            } else {
                runCatching { tempFile?.delete() }
                updateItem(index) {
                    it.copy(
                        status = JobStatus.Failed,
                        progress = 0f,
                        error = message,
                        tempPath = null,
                        publishOnly = false,
                    )
                }
            }
            runCatching { persist(context) }
        }
    }

    fun cancelAll() {
        synchronized(this) {
            cancelAllRequested = true
            activeItemJob?.cancel()
        }
        val current = _state.value
        if (current == null || !current.isRunning) {
            // 没有运行中的任务时也要给出明确结果，避免界面停在排队态。
            _state.update { run ->
                run?.copy(
                    isRunning = false,
                    items = run.items.map {
                        if (it.status == JobStatus.Queued) it.copy(status = JobStatus.Canceled) else it
                    },
                )
            }
        }
    }

    fun cancelCurrent() {
        activeItemJob?.cancel()
    }

    fun retry(context: Context): Boolean {
        synchronized(this) {
            if (runJob?.isActive == true) return false
            val current = _state.value ?: return false
            if (current.isRunning) return false
            val reset = current.items.map { item ->
                if (item.status == JobStatus.Failed || item.status == JobStatus.Canceled ||
                    item.status == JobStatus.Interrupted
                ) {
                    val tempExists = item.tempPath?.let { File(it).exists() } == true
                    item.copy(
                        status = JobStatus.Queued,
                        progress = 0f,
                        error = null,
                        publishOnly = item.publishOnly && tempExists,
                        tempPath = if (tempExists) item.tempPath else null,
                    )
                } else {
                    item
                }
            }
            if (reset.none { it.status == JobStatus.Queued }) return false
            cancelAllRequested = false
            timedOut = false
            val run = current.copy(
                items = reset,
                isRunning = true,
                currentIndex = -1,
                stopReason = null,
            )
            _state.value = run
            if (!saveBeforeStart(context)) return false
            return startService(context)
        }
    }

    /** 进程重建时恢复最近一次任务；未完成项明确标为中断，不能伪装成功。 */
    fun reconcile(context: Context) {
        if (_state.value != null) return
        val restored = try { store(context).load() } catch (e: Exception) {
            _state.value = RunState("recovery", System.currentTimeMillis(), emptyList(),
                isRunning = false, stopReason = "任务记录暂时无法读取：${e.message}")
            return
        } ?: return
        val items = restored.items.map { item ->
            val recovered = try { GifPublisher.recover(context, item) } catch (e: Exception) {
                item.copy(status = JobStatus.Failed, error = e.message)
            }
            val tempExists = recovered.tempPath?.let { File(it).isFile && File(it).length() > 0 } == true
            recovered.copy(publishOnly = tempExists && recovered.status != JobStatus.Succeeded,
                tempPath = if (tempExists) recovered.tempPath else null)
        }
        val run = restored.copy(items = items, isRunning = false, currentIndex = -1)
        _state.value = run
        if (restored.batchId == "unreadable") return
        if (runCatching { store(context).save(run) }.isFailure) return
        // 只清理本应用工作目录，保留保存失败仍可重试的完整文件。
        val retained = items.filter { it.status != JobStatus.Succeeded }.mapNotNull { it.tempPath }.toSet()
        File(context.cacheDir, "gif_work").listFiles()?.forEach { file ->
            if (file.absolutePath !in retained) file.delete()
        }
    }

    fun onServiceTimeout() {
        timedOut = true
        activeItemJob?.cancel()
        _state.update { it?.interrupted("系统结束前台任务，未完成项可重试") }
        val context = appContext ?: return
        runCatching { persist(context) }
    }

    private var appContext: Context? = null

    private fun updateItem(index: Int, transform: (RunItem) -> RunItem) {
        _state.update { run ->
            run?.copy(items = run.items.mapIndexed { i, item -> if (i == index) transform(item) else item })
        }
    }

    private fun updateProgress(index: Int, progress: Float) {
        updateItem(index) { it.copy(progress = progress.coerceIn(0f, 0.99f)) }
    }

    private fun failItem(context: Context, index: Int, message: String) {
        updateItem(index) { it.copy(status = JobStatus.Failed, error = message, progress = 0f) }
        persist(context)
    }

    private fun failRemaining(context: Context, message: String) {
        _state.update { run ->
            run?.copy(
                items = run.items.map { item ->
                    if (item.status == JobStatus.Queued) {
                        item.copy(status = JobStatus.Failed, error = message)
                    } else {
                        item
                    }
                },
            )
        }
        persist(context)
    }

    private fun markRemainingCanceled(context: Context) {
        _state.update { run ->
            run?.copy(
                items = run.items.map { item ->
                    when (item.status) {
                        JobStatus.Queued -> item.copy(status = JobStatus.Canceled, progress = 0f)
                        JobStatus.Preparing, JobStatus.Encoding, JobStatus.Publishing ->
                            item.copy(status = JobStatus.Canceled, progress = 0f)
                        else -> item
                    }
                },
            )
        }
        persist(context)
    }

    @Synchronized
    private fun persist(context: Context) {
        val state = _state.value ?: return
        store(context).save(state)
    }

    private fun startService(context: Context): Boolean = try {
        ConversionService.start(context)
        true
    } catch (e: Exception) {
        _state.update { run -> run?.copy(isRunning = false, currentIndex = -1,
            stopReason = "无法启动后台转换，请返回应用后重试",
            items = run.items.map { if (it.status == JobStatus.Queued) it.copy(
                status = JobStatus.Interrupted, error = "前台服务启动失败") else it }) }
        runCatching { persist(context) }
        false
    }

    private fun saveBeforeStart(context: Context): Boolean = try {
        persist(context)
        true
    } catch (e: Exception) {
        _state.update { it?.interrupted("无法保存任务状态，请检查存储空间后重试") }
        false
    }

    private fun freeBytes(context: Context): Long = runCatching {
        minOf(context.cacheDir.usableSpace,
            (context.getExternalFilesDir(null) ?: context.cacheDir).usableSpace)
    }.getOrDefault(0L)

    fun startContext(context: Context) {
        appContext = context.applicationContext
    }
}
