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
import com.example.video2gif.media.GifConverter
import com.example.video2gif.media.GifPublisher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var cancelAllRequested = false
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
            _state.value = run
            store(context).save(run)
            ConversionService.start(context)
            return true
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
                val itemJob = scope.launch { processItem(context, index) }
                activeItemJob = itemJob
                itemJob.join()
                activeItemJob = null
                if (cancelAllRequested) {
                    canceledAll = true
                    break
                }
            }
        } finally {
            activeItemJob = null
            if (canceledAll || cancelAllRequested) {
                markRemainingCanceled(context)
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
                persist(context)
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
                    )
                }
                persist(context)
            } else {
                updateItem(index) { it.copy(status = JobStatus.Publishing, progress = 0.94f) }
            }

            val file = tempFile ?: error("临时文件缺失")
            val published = withContext(Dispatchers.IO) {
                GifPublisher.publish(context, file, item.name)
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
                )
            }
            runCatching { file.delete() }
            persist(context)
        } catch (cancellation: CancellationException) {
            updateItem(index) {
                it.copy(
                    status = JobStatus.Canceled,
                    progress = 0f,
                    error = null,
                    tempPath = null,
                )
            }
            runCatching { tempFile?.delete() }
            persist(context)
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
            persist(context)
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
            val run = current.copy(
                items = reset,
                isRunning = true,
                currentIndex = -1,
                stopReason = null,
            )
            _state.value = run
            store(context).save(run)
            ConversionService.start(context)
            return true
        }
    }

    /** 进程重建时恢复最近一次任务；未完成项明确标为中断，不能伪装成功。 */
    fun reconcile(context: Context) {
        if (_state.value != null) return
        val restored = store(context).load() ?: return
        _state.value = restored.copy(isRunning = false, currentIndex = -1)
    }

    fun onServiceTimeout() {
        activeItemJob?.cancel()
        _state.update { run ->
            run?.copy(
                isRunning = false,
                currentIndex = -1,
                stopReason = "系统结束前台任务，未完成项可重试",
                items = run.items.map { item ->
                    if (item.status == JobStatus.Queued || item.status == JobStatus.Preparing ||
                        item.status == JobStatus.Encoding || item.status == JobStatus.Publishing
                    ) {
                        item.copy(status = JobStatus.Interrupted, progress = 0f)
                    } else {
                        item
                    }
                },
            )
        }
        val context = appContext ?: return
        _state.value?.let { store(context).save(it) }
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

    private fun persist(context: Context) {
        val state = _state.value ?: return
        store(context).save(state)
    }

    private fun freeBytes(context: Context): Long = runCatching {
        (context.getExternalFilesDir(null) ?: context.cacheDir).usableSpace
    }.getOrDefault(Long.MAX_VALUE)

    fun startContext(context: Context) {
        appContext = context.applicationContext
    }
}
