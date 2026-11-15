package com.example.video2gif.media

import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

/** 临时编码的所有权：只有完整成功返回才交给任务引擎，任何异常都删除半成品。 */
internal suspend fun <T> withTemporaryOutput(file: File, write: suspend () -> T): T {
    try {
        return write()
    } catch (t: Throwable) {
        file.delete()
        throw t
    }
}

/** 分块检查取消和目标空间；中途 ENOSPC/IO 错误交给发布事务清理。 */
internal suspend fun copyExport(
    input: InputStream,
    output: OutputStream,
    expectedBytes: Long,
    availableBytes: () -> Long,
): Long {
    val buffer = ByteArray(128 * 1024)
    var copied = 0L
    while (true) {
        coroutineContext.ensureActive()
        val count = input.read(buffer)
        if (count < 0) break
        if (availableBytes() < count) throw IOException("存储空间不足，无法保存 GIF")
        output.write(buffer, 0, count)
        copied += count
    }
    if (copied != expectedBytes) throw IOException("文件未完整复制，请重试保存")
    output.flush()
    coroutineContext.ensureActive()
    return copied
}
