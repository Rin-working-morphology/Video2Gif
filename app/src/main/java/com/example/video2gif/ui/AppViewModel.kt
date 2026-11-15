package com.example.video2gif.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.system.Os
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.video2gif.data.SettingsStore
import com.example.video2gif.domain.AppSettings
import com.example.video2gif.domain.BatchDraft
import com.example.video2gif.domain.ConversionRecipe
import com.example.video2gif.domain.CropAspect
import com.example.video2gif.domain.CropSpec
import com.example.video2gif.domain.DitherMode
import com.example.video2gif.domain.LoopMode
import com.example.video2gif.domain.MAX_BATCH_ITEMS
import com.example.video2gif.domain.OutputSpec
import com.example.video2gif.domain.QualityPreset
import com.example.video2gif.domain.RunState
import com.example.video2gif.domain.SharedParam
import com.example.video2gif.domain.SourceVideo
import com.example.video2gif.domain.ThemeMode
import com.example.video2gif.domain.VideoItemDraft
import com.example.video2gif.domain.sourceTimeMsForFrame
import com.example.video2gif.domain.outputSpec
import com.example.video2gif.media.ColorPalette
import com.example.video2gif.media.FrameDecoder
import com.example.video2gif.media.ImageOps
import com.example.video2gif.media.PaletteBuilder
import com.example.video2gif.media.PreviewFrame
import com.example.video2gif.media.PreviewRenderer
import com.example.video2gif.media.SizeEstimate
import com.example.video2gif.media.SizeEstimator
import com.example.video2gif.media.VideoProbe
import com.example.video2gif.service.ConversionEngine
import com.example.video2gif.update.UpdateChecker
import com.example.video2gif.update.UpdateInfo
import com.example.video2gif.update.UpdateStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ItemAnalysis(
    val key: Int,
    val palette: ColorPalette? = null,
    val estimate: SizeEstimate? = null,
    val analyzing: Boolean = false,
    val failed: Boolean = false,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val settingsStore = SettingsStore(app)

    val settings: StateFlow<AppSettings> = settingsStore.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AppSettings(),
    )
    val run: StateFlow<RunState?> = ConversionEngine.state

    var draft by mutableStateOf<BatchDraft?>(null)
        private set
    private val stack = mutableStateListOf<Screen>(Screen.Home)
    val screenStack: List<Screen> get() = stack
    val screen: Screen get() = stack.last()

    var activeIndex by mutableStateOf(0)
        private set
    val activeItem: VideoItemDraft? get() = draft?.items?.getOrNull(activeIndex)

    var isImporting by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set
    var showOriginal by mutableStateOf(false)
        private set
    var playheadMs by mutableStateOf(0L)
        private set
    var preview by mutableStateOf<PreviewFrame?>(null)
        private set
    var previewLoading by mutableStateOf(false)
        private set
    var previewError by mutableStateOf<String?>(null)
        private set

    var cropEditing by mutableStateOf(false)
        private set
    var cropSourceFrame by mutableStateOf<Bitmap?>(null)
        private set
    var cropSourceLoading by mutableStateOf(false)
        private set

    val thumbnails = mutableStateMapOf<String, Bitmap>()
    val timelineStrips = mutableStateMapOf<String, List<Bitmap>>()
    val analyses = mutableStateMapOf<String, ItemAnalysis>()

    private var previewDecoder: FrameDecoder? = null
    private var previewDecoderItemId: String? = null
    private var previewJob: Job? = null
    private var playbackJob: Job? = null
    private var analysisJob: Job? = null
    private var batchAnalysisJob: Job? = null
    private var previewGeneration = 0
    // ----- 设置 -----

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setDynamicColor(enabled) }
    }

    // ----- 更新检查 -----

    var updateStatus by mutableStateOf<UpdateStatus>(UpdateStatus.Idle)
        private set

    /** 发现新版本时弹一次对话框；用户关闭后不重复打扰，除非再次手动检查。 */
    var updateDialogVisible by mutableStateOf(false)
        private set

    private var updateJob: Job? = null

    fun checkForUpdates() {
        if (updateJob?.isActive == true) return
        updateStatus = UpdateStatus.Checking
        updateJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { UpdateChecker.fetch() }.fold(
                    onSuccess = { info ->
                        if (info.isNewerThan(UpdateChecker.currentVersionCode(app))) {
                            UpdateStatus.Available(info)
                        } else {
                            UpdateStatus.UpToDate
                        }
                    },
                    onFailure = { UpdateStatus.Failed(UpdateChecker.userFacingError(it)) },
                )
            }
            updateStatus = result
            when (result) {
                is UpdateStatus.Available -> updateDialogVisible = true
                UpdateStatus.UpToDate -> notice = "已是最新版本"
                is UpdateStatus.Failed -> notice = result.reason
                else -> Unit
            }
        }
    }

    fun dismissUpdateDialog() {
        updateDialogVisible = false
    }

    /** 下载走系统浏览器与 GitHub Release 页面，应用本身不申请安装未知应用权限。 */
    fun openUpdatePage(info: UpdateInfo) {
        updateDialogVisible = false
        val opened = runCatching {
            app.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        if (opened) return
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Video2Gif 发布页", info.releaseUrl))
        notice = "没有可打开网页的应用，下载链接已复制"
    }

    // ----- 导航 -----

    fun navigate(target: Screen) {
        if (stack.last() == target) return
        stack += target
        onEnter(target)
    }

    fun back() {
        if (stack.size <= 1) return
        val leaving = stack.removeAt(stack.lastIndex)
        onLeave(leaving)
    }

    /** 回到首页但保留任务与草稿状态；用于结果页与进度页。 */
    fun goHome() {
        while (stack.size > 1) {
            val leaving = stack.removeAt(stack.lastIndex)
            onLeave(leaving)
        }
    }

    private fun onEnter(target: Screen) {
        when (target) {
            Screen.Editor -> {
                if (activeItem?.source != null) {
                    playheadMs = activeItem!!.recipe.trimStartMs
                    ensureActiveResources()
                }
            }
            Screen.Batch -> ensureBatchAnalyses()
            else -> Unit
        }
    }

    private fun onLeave(screen: Screen) {
        when (screen) {
            Screen.Editor -> {
                stopPlayback()
                releasePreview()
            }
            Screen.Batch -> {
                // 批次分析可以继续，返回时直接复用结果。
            }
            else -> Unit
        }
    }

    fun consumeNotice() {
        notice = null
    }

    fun showNotice(message: String) {
        notice = message
    }

    // ----- 导入 -----

    fun importVideos(uris: List<Uri>, startNewBatch: Boolean = false) {
        if (uris.isEmpty() || isImporting) return
        val current = if (startNewBatch) null else draft
        val room = MAX_BATCH_ITEMS - (current?.items?.size ?: 0)
        if (uris.size > room) {
            notice = "每批最多 $MAX_BATCH_ITEMS 个视频，请减少选择"
            return
        }
        // 首页选择成功后才开始新批次；取消选择不会改动当前编辑内容。
        if (startNewBatch) discardDraft()
        isImporting = true
        viewModelScope.launch {
            val defaults = current?.defaults ?: ConversionRecipe()
            val baseIndex = current?.items?.size ?: 0
            val results = withContext(Dispatchers.IO) {
                uris.mapIndexed { index, uri -> loadItem(uri, defaults, baseIndex + index) }
            }
            val items = current?.items.orEmpty() + results.map { it.first }
            val batch = BatchDraft(
                id = current?.id ?: UUID.randomUUID().toString(),
                defaults = defaults,
                items = items,
                updatedAt = System.currentTimeMillis(),
            )
            draft = batch
            results.forEach { (item, thumb) ->
                if (thumb != null) thumbnails[item.id] = thumb
                val quick = item.recipe.outputSpec(item.source)?.let {
                    SizeEstimator.quick(it, item.recipe.colorCount)
                }
                analyses[item.id] = ItemAnalysis(
                    key = analysisKey(item),
                    estimate = quick,
                    analyzing = item.source != null,
                )
            }
            activeIndex = batch.items.indexOfFirst { it.source != null }
                .let { if (it < 0) 0 else it }
            isImporting = false
            val failed = results.count { it.first.error != null }
            if (failed > 0) {
                notice = if (failed == results.size) {
                    "这台设备暂时无法处理所选视频"
                } else {
                    "$failed 个视频无法读取，请在批次中移除"
                }
            }
            if (batch.items.any { it.source != null }) {
                navigate(Screen.Editor)
                ensureActiveResources()
            } else {
                navigate(Screen.Batch)
            }
        }
    }

    private fun loadItem(
        uri: Uri,
        defaults: ConversionRecipe,
        fallbackIndex: Int,
    ): Pair<VideoItemDraft, Bitmap?> {
        runCatching {
            app.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val name = resolveDisplayName(uri, fallbackIndex)
        val source = VideoProbe.probe(app, uri)
        val thumb = if (source != null) VideoProbe.thumbnail(app, uri, 480) else null
        if (source == null) {
            return VideoItemDraft(
                id = UUID.randomUUID().toString(),
                uri = uri.toString(),
                name = name,
                source = null,
                recipe = defaults.copy(trimStartMs = 0, trimEndMs = 0),
                error = "这台设备暂时无法处理此视频",
            ) to null
        }
        val recipe = defaults.copy(
            trimStartMs = 0,
            trimEndMs = source.durationMs,
            crop = CropSpec.Full,
        )
        return VideoItemDraft(
            id = UUID.randomUUID().toString(),
            uri = uri.toString(),
            name = name,
            source = source,
            recipe = recipe,
        ) to thumb
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
    }

    /**
     * Photo Picker 对部分本地项目只返回「37.mp4」这类 ID 文件名。
     * 依次尝试 MediaStore、文件描述符的真实路径，最后退回「视频 N」，避免把内部 ID 当作品名。
     */
    private fun resolveDisplayName(uri: Uri, fallbackIndex: Int): String {
        val fromColumn = queryDisplayName(uri)
        if (fromColumn != null && !looksLikeInternalName(fromColumn)) return fromColumn
        val mediaId = uri.lastPathSegment?.toLongOrNull()
        if (mediaId != null) {
            val fromStore = runCatching {
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
                app.contentResolver.query(
                    collection,
                    arrayOf(MediaStore.Video.Media.DISPLAY_NAME),
                    "${MediaStore.Video.Media._ID} = ?",
                    arrayOf(mediaId.toString()),
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()
            if (fromStore != null && !looksLikeInternalName(fromStore)) return fromStore
        }
        val fromPath = runCatching {
            app.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                File(Os.readlink("/proc/self/fd/${pfd.fd}")).name
            }
        }.getOrNull()
        if (fromPath != null && fromPath.isNotBlank() && !fromPath.contains("pipe:") &&
            !fromPath.startsWith("/dev/") && !looksLikeInternalName(fromPath)
        ) {
            return fromPath
        }
        if (fromColumn != null && !looksLikeInternalName(fromColumn)) return fromColumn
        return "视频 ${fallbackIndex + 1}"
    }

    private fun looksLikeInternalName(name: String): Boolean =
        Regex("^\\d+\\.[A-Za-z0-9]{1,5}$").matches(name)

    fun ensureThumbnails(items: List<VideoItemDraft>) {
        viewModelScope.launch(Dispatchers.IO) {
            items.forEach { item ->
                if (thumbnails[item.id] != null) return@forEach
                val thumb = VideoProbe.thumbnail(app, Uri.parse(item.uri), 480)
                if (thumb != null) {
                    withContext(Dispatchers.Main) { thumbnails[item.id] = thumb }
                } else if (item.source != null) {
                    withContext(Dispatchers.Main) {
                        updateItemById(item.id) {
                            it.copy(error = "视频已移动或无法读取，请重新选择")
                        }
                    }
                }
            }
        }
    }

    fun discardDraft() {
        stopPlayback()
        releasePreview()
        analysisJob?.cancel()
        batchAnalysisJob?.cancel()
        draft = null
        activeIndex = 0
        preview = null
        thumbnails.clear()
        timelineStrips.clear()
        analyses.clear()
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    // ----- 编辑 -----

    fun selectItemAt(index: Int) {
        val batch = draft ?: return
        if (index !in batch.items.indices || index == activeIndex) return
        stopPlayback()
        analysisJob?.cancel()
        releasePreview()
        cropEditing = false
        cropSourceFrame = null
        activeIndex = index
        preview = null
        previewError = null
        playheadMs = activeItem?.recipe?.trimStartMs ?: 0L
        if (activeItem?.source != null) {
            ensureActiveResources()
        }
    }

    fun selectNextItem() {
        val size = draft?.items?.size ?: return
        selectItemAt((activeIndex + 1) % size)
    }

    fun updateActiveRecipe(
        overrides: Set<SharedParam>,
        transform: (ConversionRecipe) -> ConversionRecipe,
    ) {
        val item = activeItem ?: return
        val updated = item.copy(
            recipe = transform(item.recipe),
            overrides = item.overrides + overrides,
        )
        replaceItem(updated)
        afterRecipeChange()
    }

    private fun replaceItem(updated: VideoItemDraft) {
        val batch = draft ?: return
        draft = batch.copy(
            items = batch.items.map { if (it.id == updated.id) updated else it },
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun updateItemById(id: String, transform: (VideoItemDraft) -> VideoItemDraft) {
        val batch = draft ?: return
        val item = batch.items.firstOrNull { it.id == id } ?: return
        replaceItem(transform(item))
    }

    private fun afterRecipeChange() {
        val item = activeItem ?: return
        val source = item.source ?: return
        val end = item.recipe.trimEndMs.coerceIn(1, source.durationMs)
        playheadMs = playheadMs.coerceIn(item.recipe.trimStartMs, (end - 1).coerceAtLeast(item.recipe.trimStartMs))
        val spec = item.recipe.outputSpec(source)
        if (spec != null) {
            val existing = analyses[item.id]
            if (existing?.key != analysisKey(item) || existing.palette == null) {
                analyses[item.id] = ItemAnalysis(
                    key = analysisKey(item),
                    palette = existing?.palette,
                    estimate = SizeEstimator.quick(spec, item.recipe.colorCount),
                    analyzing = true,
                )
            }
        }
        scheduleAnalysis()
        if (cropEditing) refreshCropSource()
        if (isPlaying) restartPlayback() else requestPreviewFrame()
    }

    fun setQuality(preset: QualityPreset) {
        updateActiveRecipe(setOf(SharedParam.Quality)) {
            it.copy(quality = preset, colorCount = preset.colorCount, dither = preset.dither)
        }
    }

    fun setColorCount(count: Int) {
        updateActiveRecipe(setOf(SharedParam.ColorCount)) { it.withColors(count) }
    }

    fun setDither(mode: DitherMode) {
        updateActiveRecipe(setOf(SharedParam.Dither)) { it.withDither(mode) }
    }

    fun setFps(fps: Int) {
        updateActiveRecipe(setOf(SharedParam.Fps)) { it.copy(fps = fps) }
    }

    fun setLongSide(px: Int) {
        updateActiveRecipe(setOf(SharedParam.LongSide)) { it.copy(longSide = px.coerceIn(64, 1080)) }
    }

    fun setSpeed(speed: Float) {
        updateActiveRecipe(setOf(SharedParam.Speed)) { it.copy(speed = speed) }
    }

    fun setLoopMode(loop: LoopMode, count: Int = activeItem?.recipe?.loopCount ?: 3) {
        updateActiveRecipe(setOf(SharedParam.Loop)) { it.copy(loop = loop, loopCount = count) }
    }

    fun setLoopCount(count: Int) {
        updateActiveRecipe(setOf(SharedParam.Loop)) {
            it.copy(loop = LoopMode.Custom, loopCount = count.coerceIn(1, 20))
        }
    }

    fun setRotation(degrees: Int) {
        val source = activeItem?.source ?: return
        val normalized = ((degrees % 360) + 360) % 360
        val rotatedW = if (normalized % 180 == 90) source.displayHeight else source.displayWidth
        val rotatedH = if (normalized % 180 == 90) source.displayWidth else source.displayHeight
        val aspect = activeItem?.recipe?.crop?.aspect ?: CropAspect.Original
        val crop = if (aspect == CropAspect.Original || aspect == CropAspect.Free) {
            CropSpec.Full
        } else {
            CropSpec.centered(aspect, rotatedW, rotatedH)
        }
        updateActiveRecipe(setOf(SharedParam.Rotation, SharedParam.Crop)) {
            it.copy(userRotation = normalized, crop = crop)
        }
    }

    fun setCrop(crop: CropSpec) {
        updateActiveRecipe(setOf(SharedParam.Crop)) { it.copy(crop = crop) }
        cropEditing = false
        cropSourceFrame = null
    }

    fun setCropAspect(aspect: CropAspect) {
        val source = activeItem?.source ?: return
        val recipe = activeItem?.recipe ?: return
        val rotatedW = if (recipe.userRotation % 180 == 90) source.displayHeight else source.displayWidth
        val rotatedH = if (recipe.userRotation % 180 == 90) source.displayWidth else source.displayHeight
        val crop = CropSpec.centered(aspect, rotatedW, rotatedH)
        updateActiveRecipe(setOf(SharedParam.Crop)) { it.copy(crop = crop) }
    }

    fun startCropEditing() {
        if (activeItem?.source == null) return
        cropEditing = true
        refreshCropSource()
    }

    fun cancelCropEditing() {
        cropEditing = false
        cropSourceFrame = null
        cropSourceLoading = false
    }

    private fun refreshCropSource() {
        val item = activeItem ?: return
        val source = item.source ?: return
        val decoder = decoderFor(item)
        val time = playheadMs
        val rotation = item.recipe.userRotation
        cropSourceLoading = true
        viewModelScope.launch(Dispatchers.Default) {
            val decoded = try {
                decoder.decode(time, source, 720)
            } catch (_: Exception) {
                null
            }
            val rotated = decoded?.let { ImageOps.applyRotation(it, rotation) }
            val scaled = rotated?.let {
                val longSide = max(it.width, it.height)
                if (longSide > 720) {
                    val scale = 720f / longSide
                    ImageOps.scale(
                        it,
                        (it.width * scale).roundToInt().coerceAtLeast(2),
                        (it.height * scale).roundToInt().coerceAtLeast(2),
                    )
                } else {
                    it
                }
            }
            withContext(Dispatchers.Main) {
                if (cropEditing) {
                    cropSourceFrame = scaled
                }
                cropSourceLoading = false
            }
        }
    }

    fun setTrim(startMs: Long, endMs: Long) {
        val item = activeItem ?: return
        val source = item.source ?: return
        val duration = source.durationMs
        val minGap = 200L
        var start = startMs.coerceIn(0, (duration - minGap).coerceAtLeast(0))
        var end = endMs.coerceIn(start + minGap, duration)
        if (end - start < minGap) {
            end = (start + minGap).coerceAtMost(duration)
            start = (end - minGap).coerceAtLeast(0)
        }
        updateActiveRecipe(emptySet()) { it.copy(trimStartMs = start, trimEndMs = end) }
        playheadMs = playheadMs.coerceIn(start, (end - 1).coerceAtLeast(start))
    }

    fun seekTo(timeMs: Long) {
        val item = activeItem ?: return
        val start = item.recipe.trimStartMs
        val end = (item.recipe.trimEndMs - 1).coerceAtLeast(start)
        stopPlayback()
        playheadMs = timeMs.coerceIn(start, end)
        requestPreviewFrame()
    }

    fun toggleOriginal() {
        showOriginal = !showOriginal
    }

    fun togglePlayback() {
        if (isPlaying) stopPlayback() else startPlayback()
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        isPlaying = false
    }

    private fun startPlayback() {
        val item = activeItem ?: return
        val source = item.source ?: return
        val spec = item.recipe.outputSpec(source) ?: return
        val decoder = previewDecoder ?: return
        val start = item.recipe.trimStartMs
        val end = item.recipe.trimEndMs.coerceAtLeast(start + 1)
        if (playheadMs >= end - 10) playheadMs = start
        val palette = freshAnalysis(item.id)?.palette
        isPlaying = true
        previewError = null
        previewLoading = false
        val job = viewModelScope.launch(Dispatchers.Default) {
            var originWall = SystemClock.elapsedRealtime()
            var originSource = playheadMs
            var plays = 0
            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - originWall
                val time = originSource + (elapsed * item.recipe.speed).toLong()
                if (time >= end - 1) {
                    plays++
                    val stop = when (item.recipe.loop) {
                        LoopMode.Once -> true
                        LoopMode.Custom -> plays >= item.recipe.loopCount
                        LoopMode.Infinite -> false
                    }
                    if (stop) {
                        withContext(Dispatchers.Main) {
                            playheadMs = end - 1
                            isPlaying = false
                        }
                        break
                    }
                    originWall = SystemClock.elapsedRealtime()
                    originSource = start
                }
                val frameIndex = (((if (time >= end - 1) start else time) - start) /
                    item.recipe.speed * item.recipe.fps / 1000.0).toInt().coerceIn(0, spec.frameCount - 1)
                val sourceTime = item.recipe.sourceTimeMsForFrame(source, frameIndex)
                val frame = try {
                    PreviewRenderer.render(decoder, source, item.recipe, spec, sourceTime, palette)
                } catch (_: Exception) {
                    null
                }
                if (frame != null) {
                    withContext(Dispatchers.Main) {
                        if (isPlaying && activeItem?.id == item.id) {
                            preview = frame
                            playheadMs = sourceTime
                            previewLoading = false
                        }
                    }
                }
                delay(16)
            }
        }
        playbackJob = job
        job.invokeOnCompletion {
            viewModelScope.launch(Dispatchers.Main) {
                if (playbackJob === job) {
                    playbackJob = null
                    isPlaying = false
                }
            }
        }
    }

    private fun restartPlayback() {
        stopPlayback()
        startPlayback()
    }

    // ----- 预览 -----

    private fun ensureActiveResources() {
        val item = activeItem ?: return
        if (item.source == null) return
        ensureThumbnail(item)
        ensureTimelineStrip(item)
        val existing = analyses[item.id]
        if (existing == null || existing.key != analysisKey(item) || existing.palette == null) {
            val quick = item.recipe.outputSpec(item.source)?.let {
                SizeEstimator.quick(it, item.recipe.colorCount)
            }
            analyses[item.id] = ItemAnalysis(
                key = analysisKey(item),
                estimate = existing?.estimate ?: quick,
                analyzing = true,
            )
            scheduleAnalysis()
        }
        requestPreviewFrame()
    }

    private fun ensureThumbnail(item: VideoItemDraft) {
        if (thumbnails[item.id] != null) return
        viewModelScope.launch(Dispatchers.IO) {
            val thumb = VideoProbe.thumbnail(app, Uri.parse(item.uri), 480)
            if (thumb != null) withContext(Dispatchers.Main) { thumbnails[item.id] = thumb }
        }
    }

    private fun ensureTimelineStrip(item: VideoItemDraft) {
        if (timelineStrips.containsKey(item.id)) return
        val source = item.source ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val decoder = FrameDecoder(app, Uri.parse(item.uri))
            val frames = ArrayList<Bitmap>(8)
            try {
                val duration = source.durationMs.coerceAtLeast(1)
                for (i in 0 until 8) {
                    val time = (duration * (i + 0.5) / 8.0).toLong()
                    val bitmap = decoder.decode(time, source, 120) ?: continue
                    val height = 96
                    val width = (bitmap.width.toFloat() * height / bitmap.height.coerceAtLeast(1))
                        .roundToInt().coerceAtLeast(8)
                    frames += ImageOps.scale(bitmap, width, height)
                }
            } catch (_: Exception) {
            } finally {
                decoder.close()
            }
            if (frames.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    timelineStrips[item.id] = frames
                }
            }
        }
    }

    private fun releasePreview() {
        previewJob?.cancel()
        previewDecoder?.close()
        previewDecoder = null
        previewDecoderItemId = null
        preview = null
        previewLoading = false
    }

    private fun decoderFor(item: VideoItemDraft): FrameDecoder {
        if (previewDecoderItemId != item.id || previewDecoder == null) {
            previewDecoder?.close()
            previewDecoder = FrameDecoder(app, Uri.parse(item.uri))
            previewDecoderItemId = item.id
        }
        return previewDecoder!!
    }

    private fun requestPreviewFrame() {
        val item = activeItem ?: return
        val source = item.source ?: return
        val spec = item.recipe.outputSpec(source) ?: return
        val decoder = decoderFor(item)
        val palette = freshAnalysis(item.id)?.palette
        val generation = ++previewGeneration
        previewJob?.cancel()
        previewLoading = true
        previewJob = viewModelScope.launch(Dispatchers.Default) {
            val frame = try {
                PreviewRenderer.render(decoder, source, item.recipe, spec, playheadMs, palette)
            } catch (_: Exception) {
                null
            }
            withContext(Dispatchers.Main) {
                if (generation != previewGeneration) return@withContext
                previewLoading = false
                if (frame != null) {
                    preview = frame
                    previewError = null
                } else {
                    previewError = "预览暂时不可用"
                }
            }
        }
    }

    // ----- 分析与估算 -----

    private fun analysisKey(item: VideoItemDraft): Int =
        item.recipe.hashCode() * 31 + item.source.hashCode()

    fun freshAnalysis(itemId: String): ItemAnalysis? {
        val item = draft?.items?.firstOrNull { it.id == itemId } ?: return null
        val analysis = analyses[itemId] ?: return null
        return if (analysis.key == analysisKey(item)) analysis else null
    }

    fun estimateFor(item: VideoItemDraft): SizeEstimate? {
        val spec = item.recipe.outputSpec(item.source) ?: return null
        val analysis = analyses[item.id]
        if (analysis != null && analysis.key == analysisKey(item) && analysis.estimate != null) {
            return analysis.estimate
        }
        return SizeEstimator.quick(spec, item.recipe.colorCount)
    }

    fun estimateFor(id: String): SizeEstimate? {
        val item = draft?.items?.firstOrNull { it.id == id } ?: return null
        return estimateFor(item)
    }

    fun analyzingIds(): Set<String> = analyses.filterValues { it.analyzing }.keys

    private fun scheduleAnalysis(immediate: Boolean = false) {
        val item = activeItem ?: return
        val source = item.source ?: return
        val spec = item.recipe.outputSpec(source) ?: return
        val key = analysisKey(item)
        val current = analyses[item.id]
        if (current?.key == key && current.palette != null && !current.analyzing) return
        analysisJob?.cancel()
        val targetId = item.id
        analysisJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                if (!immediate) delay(240)
                withContext(Dispatchers.Main) {
                    analyses[targetId] = (analyses[targetId] ?: ItemAnalysis(key)).copy(
                        key = key,
                        analyzing = true,
                        failed = false,
                    )
                }
                val decoder = FrameDecoder(app, Uri.parse(item.uri))
                try {
                    val palette = PaletteBuilder.build(decoder, source, item.recipe, spec, item.recipe.colorCount)
                    val result = PaletteBuilder.analyze(decoder, source, item.recipe, spec, palette)
                    withContext(Dispatchers.Main) {
                        if (activeItem?.id == targetId) {
                            analyses[targetId] = ItemAnalysis(
                                key = key,
                                palette = result.palette,
                                estimate = result.estimate,
                                analyzing = false,
                            )
                            if (isPlaying) restartPlayback() else requestPreviewFrame()
                        } else {
                            analyses[targetId] = ItemAnalysis(
                                key = key,
                                palette = result.palette,
                                estimate = result.estimate,
                                analyzing = false,
                            )
                        }
                    }
                } finally {
                    decoder.close()
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    analyses[targetId] = (analyses[targetId] ?: ItemAnalysis(key)).copy(
                        analyzing = false,
                        failed = true,
                    )
                }
            }
        }
    }

    /** 批次页顺序补齐未分析项；正式转换开始时取消，避免与编码抢解码器。 */
    fun ensureBatchAnalyses() {
        val batch = draft ?: return
        if (batchAnalysisJob?.isActive == true) return
        batchAnalysisJob = viewModelScope.launch(Dispatchers.Default) {
            for (item in batch.items) {
                if (!isActive) break
                val source = item.source ?: continue
                val spec = item.recipe.outputSpec(source) ?: continue
                val key = analysisKey(item)
                val existing = analyses[item.id]
                if (existing?.key == key && existing.palette != null && !existing.analyzing) continue
                withContext(Dispatchers.Main) {
                    analyses[item.id] = ItemAnalysis(key = key, estimate = existing?.estimate, analyzing = true)
                }
                val decoder = FrameDecoder(app, Uri.parse(item.uri))
                try {
                    val palette = PaletteBuilder.build(decoder, source, item.recipe, spec, item.recipe.colorCount)
                    val result = PaletteBuilder.analyze(decoder, source, item.recipe, spec, palette)
                    withContext(Dispatchers.Main) {
                        analyses[item.id] = ItemAnalysis(
                            key = key,
                            palette = result.palette,
                            estimate = result.estimate,
                            analyzing = false,
                        )
                    }
                } catch (c: CancellationException) {
                    throw c
                } catch (_: Throwable) {
                    withContext(Dispatchers.Main) {
                        analyses[item.id] = (analyses[item.id] ?: ItemAnalysis(key)).copy(analyzing = false)
                    }
                } finally {
                    decoder.close()
                }
            }
        }
    }

    // ----- 批次 -----

    fun removeItem(id: String) {
        val batch = draft ?: return
        val activeId = activeItem?.id
        val remaining = batch.items.filterNot { it.id == id }
        if (remaining.isEmpty()) {
            discardDraft()
            return
        }
        draft = batch.copy(items = remaining, updatedAt = System.currentTimeMillis())
        thumbnails.remove(id)
        timelineStrips.remove(id)
        analyses.remove(id)
        val stillThere = remaining.indexOfFirst { it.id == activeId }
        if (stillThere >= 0) {
            activeIndex = stillThere
        } else {
            activeIndex = activeIndex.coerceIn(0, remaining.lastIndex)
            stopPlayback()
            releasePreview()
            playheadMs = activeItem?.recipe?.trimStartMs ?: 0L
            if (activeItem?.source != null) ensureActiveResources()
        }
    }

    /** 统一参数：默认只写入没有单独覆盖的项，覆盖模式会清除对应覆盖标记。 */
    fun applyUnifiedParameters(
        quality: QualityPreset?,
        fps: Int?,
        longSide: Int?,
        speed: Float?,
        loop: LoopMode?,
        overwrite: Boolean,
    ) {
        val batch = draft ?: return
        var defaults = batch.defaults
        if (quality != null) {
            defaults = defaults.copy(
                quality = quality,
                colorCount = quality.colorCount,
                dither = quality.dither,
            )
        }
        if (fps != null) defaults = defaults.copy(fps = fps)
        if (longSide != null) defaults = defaults.copy(longSide = longSide)
        if (speed != null) defaults = defaults.copy(speed = speed)
        if (loop != null) defaults = defaults.copy(loop = loop)

        val items = batch.items.map { item ->
            var recipe = item.recipe
            val overrides = item.overrides.toMutableSet()
            if (quality != null && (overwrite || overrides.none {
                    it == SharedParam.Quality || it == SharedParam.ColorCount || it == SharedParam.Dither
                })
            ) {
                recipe = recipe.copy(
                    quality = quality,
                    colorCount = quality.colorCount,
                    dither = quality.dither,
                )
                overrides.remove(SharedParam.Quality)
                overrides.remove(SharedParam.ColorCount)
                overrides.remove(SharedParam.Dither)
            }
            if (fps != null && (overwrite || SharedParam.Fps !in overrides)) {
                recipe = recipe.copy(fps = fps)
                overrides.remove(SharedParam.Fps)
            }
            if (longSide != null && (overwrite || SharedParam.LongSide !in overrides)) {
                recipe = recipe.copy(longSide = longSide)
                overrides.remove(SharedParam.LongSide)
            }
            if (speed != null && (overwrite || SharedParam.Speed !in overrides)) {
                recipe = recipe.copy(speed = speed)
                overrides.remove(SharedParam.Speed)
            }
            if (loop != null && (overwrite || SharedParam.Loop !in overrides)) {
                recipe = recipe.copy(loop = loop)
                overrides.remove(SharedParam.Loop)
            }
            item.copy(recipe = recipe, overrides = overrides)
        }
        draft = batch.copy(defaults = defaults, items = items, updatedAt = System.currentTimeMillis())
        ensureBatchAnalyses()
    }

    fun clearOverrides() {
        val batch = draft ?: return
        if (batch.items.none { it.overrides.isNotEmpty() }) return
        val defaults = batch.defaults
        val items = batch.items.map { item ->
            val recipe = item.recipe.copy(
                quality = defaults.quality,
                colorCount = defaults.colorCount,
                dither = defaults.dither,
                fps = defaults.fps,
                longSide = defaults.longSide,
                speed = defaults.speed,
                loop = defaults.loop,
                loopCount = defaults.loopCount,
            )
            item.copy(recipe = recipe, overrides = emptySet())
        }
        draft = batch.copy(items = items, updatedAt = System.currentTimeMillis())
        ensureBatchAnalyses()
    }

    // ----- 转换 -----

    fun startConversion() {
        val batch = draft ?: return
        if (batch.items.isEmpty()) {
            notice = "先选择视频"
            return
        }
        if (batch.items.any { it.source == null || it.error != null }) {
            notice = "请先在批次中移除无法读取的视频"
            navigate(Screen.Batch)
            return
        }
        if (run.value?.isRunning == true) {
            notice = "已有转换任务在运行"
            navigate(Screen.Progress)
            return
        }
        stopPlayback()
        releasePreview()
        analysisJob?.cancel()
        batchAnalysisJob?.cancel()
        val started = ConversionEngine.begin(app, batch)
        if (started) {
            navigate(Screen.Progress)
        } else {
            notice = "无法开始转换，请稍后再试"
        }
    }

    fun cancelAllConversion() {
        ConversionEngine.cancelAll()
    }

    fun cancelCurrentConversion() {
        ConversionEngine.cancelCurrent()
    }

    fun retryConversion() {
        if (ConversionEngine.retry(app)) {
            navigate(Screen.Progress)
        } else {
            notice = "没有可重试的项目"
        }
    }

    fun openProgress() {
        if (run.value != null) navigate(Screen.Progress)
    }

    fun openResult() {
        if (run.value != null) navigate(Screen.Result)
    }

    fun outputSpecFor(item: VideoItemDraft?): OutputSpec? =
        if (item == null) null else item.recipe.outputSpec(item.source)

    override fun onCleared() {
        stopPlayback()
        previewDecoder?.close()
    }
}
