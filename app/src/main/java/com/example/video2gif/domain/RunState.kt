package com.example.video2gif.domain

enum class JobStatus { Queued, Preparing, Encoding, Publishing, Succeeded, Failed, Canceled, Interrupted }

data class RunItem(
    val id: String,
    val name: String,
    val uri: String,
    val source: SourceVideo?,
    val recipe: ConversionRecipe,
    val status: JobStatus = JobStatus.Queued,
    val progress: Float = 0f,
    val error: String? = null,
    val tempPath: String? = null,
    val outputUri: String? = null,
    val outputName: String? = null,
    val outputSizeBytes: Long = 0L,
    val locationLabel: String? = null,
    val publishOnly: Boolean = false,
    /** 发布前先持久化目标；完整复制后才允许公开。 */
    val pendingUri: String? = null,
    val publishReady: Boolean = false,
)

data class RunState(
    val batchId: String,
    val startedAt: Long,
    val items: List<RunItem>,
    val currentIndex: Int = -1,
    val isRunning: Boolean = true,
    val stopReason: String? = null,
) {
    val succeededCount: Int get() = items.count { it.status == JobStatus.Succeeded }
    val failedCount: Int get() = items.count { it.status == JobStatus.Failed }
    val canceledCount: Int get() = items.count { it.status == JobStatus.Canceled }
    val interruptedCount: Int get() = items.count { it.status == JobStatus.Interrupted }
    val pendingCount: Int get() = items.count {
        it.status == JobStatus.Queued || it.status == JobStatus.Preparing ||
            it.status == JobStatus.Encoding || it.status == JobStatus.Publishing
    }
    val finishedCount: Int get() = items.count {
        it.status == JobStatus.Succeeded || it.status == JobStatus.Failed ||
            it.status == JobStatus.Canceled || it.status == JobStatus.Interrupted
    }
    val totalProgress: Float
        get() {
            if (items.isEmpty()) return 0f
            val sum = items.sumOf { item ->
                when (item.status) {
                    JobStatus.Succeeded, JobStatus.Failed, JobStatus.Canceled, JobStatus.Interrupted -> 1.0
                    else -> item.progress.toDouble()
                }
            }
            return (sum / items.size).toFloat().coerceIn(0f, 1f)
        }

    val currentItem: RunItem? get() = items.getOrNull(currentIndex)
    val hasRetryable: Boolean get() = items.any {
        it.status == JobStatus.Failed || it.status == JobStatus.Canceled || it.status == JobStatus.Interrupted
    }
}
