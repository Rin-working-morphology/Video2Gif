package com.example.video2gif.domain

val JobStatus.isPending: Boolean
    get() = this == JobStatus.Queued || this == JobStatus.Preparing ||
        this == JobStatus.Encoding || this == JobStatus.Publishing

/** 超时与进程退出共用中断语义；成功项和保存检查点原样保留。 */
fun RunState.interrupted(reason: String? = stopReason): RunState = copy(
    isRunning = false,
    currentIndex = -1,
    stopReason = reason,
    items = items.map { if (it.status.isPending) it.copy(status = JobStatus.Interrupted, progress = 0f) else it },
)

fun RunItem.canceled(timedOut: Boolean, completeTempPath: String?): RunItem = copy(
    status = if (timedOut) JobStatus.Interrupted else JobStatus.Canceled,
    progress = 0f,
    error = null,
    tempPath = if (timedOut) completeTempPath else null,
    publishOnly = timedOut && completeTempPath != null,
)
