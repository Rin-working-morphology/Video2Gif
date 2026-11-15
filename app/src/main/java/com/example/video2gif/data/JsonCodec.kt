package com.example.video2gif.data

import com.example.video2gif.domain.ConversionRecipe
import com.example.video2gif.domain.CropAspect
import com.example.video2gif.domain.CropSpec
import com.example.video2gif.domain.DitherMode
import com.example.video2gif.domain.JobStatus
import com.example.video2gif.domain.LoopMode
import com.example.video2gif.domain.QualityPreset
import com.example.video2gif.domain.RunItem
import com.example.video2gif.domain.RunState
import com.example.video2gif.domain.SourceVideo
import org.json.JSONArray
import org.json.JSONObject

/** 转换任务快照的 JSON 编码。 */
object JsonCodec {
    const val RUN_SCHEMA_VERSION = 2

    fun sourceToJson(source: SourceVideo): JSONObject = JSONObject().apply {
        put("displayWidth", source.displayWidth)
        put("displayHeight", source.displayHeight)
        put("rotation", source.rotationDegrees)
        put("duration", source.durationMs)
        put("mime", source.mimeType)
        source.nominalFrameRate?.let { put("fps", it.toDouble()) }
    }

    fun sourceFromJson(json: JSONObject?): SourceVideo? {
        if (json == null) return null
        return try {
            SourceVideo(
                displayWidth = json.getInt("displayWidth"),
                displayHeight = json.getInt("displayHeight"),
                rotationDegrees = json.optInt("rotation", 0),
                durationMs = json.getLong("duration"),
                mimeType = json.optString("mime").takeIf { it.isNotBlank() && it != "null" },
                nominalFrameRate = if (json.has("fps")) json.getDouble("fps").toFloat() else null,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun cropToJson(crop: CropSpec): JSONObject = JSONObject().apply {
        put("l", crop.left.toDouble())
        put("t", crop.top.toDouble())
        put("r", crop.right.toDouble())
        put("b", crop.bottom.toDouble())
        put("aspect", crop.aspect.name)
    }

    private fun cropFromJson(json: JSONObject?): CropSpec {
        if (json == null) return CropSpec.Full
        return try {
            CropSpec(
                left = json.getDouble("l").toFloat(),
                top = json.getDouble("t").toFloat(),
                right = json.getDouble("r").toFloat(),
                bottom = json.getDouble("b").toFloat(),
                aspect = runCatching { CropAspect.valueOf(json.getString("aspect")) }
                    .getOrDefault(CropAspect.Original),
            )
        } catch (_: Exception) {
            CropSpec.Full
        }
    }

    fun recipeToJson(recipe: ConversionRecipe): JSONObject = JSONObject().apply {
        put("quality", recipe.quality.name)
        put("colors", recipe.colorCount)
        put("dither", recipe.dither.name)
        put("fps", recipe.fps)
        put("longSide", recipe.longSide)
        put("speed", recipe.speed.toDouble())
        put("loop", recipe.loop.name)
        put("loopCount", recipe.loopCount)
        put("rotation", recipe.userRotation)
        put("trimStart", recipe.trimStartMs)
        put("trimEnd", recipe.trimEndMs)
        put("crop", cropToJson(recipe.crop))
    }

    fun recipeFromJson(json: JSONObject?): ConversionRecipe {
        val fallback = ConversionRecipe()
        if (json == null) return fallback
        return try {
            ConversionRecipe(
                quality = runCatching { QualityPreset.valueOf(json.getString("quality")) }
                    .getOrDefault(fallback.quality),
                colorCount = json.optInt("colors", fallback.colorCount).coerceIn(4, 256),
                dither = runCatching { DitherMode.valueOf(json.getString("dither")) }
                    .getOrDefault(fallback.dither),
                fps = json.optInt("fps", fallback.fps).coerceIn(1, 60),
                longSide = json.optInt("longSide", fallback.longSide).coerceIn(64, 1080),
                speed = json.optDouble("speed", fallback.speed.toDouble()).toFloat()
                    .coerceIn(0.25f, 4f),
                loop = runCatching { LoopMode.valueOf(json.getString("loop")) }
                    .getOrDefault(fallback.loop),
                loopCount = json.optInt("loopCount", fallback.loopCount).coerceIn(1, 99),
                userRotation = json.optInt("rotation", 0),
                trimStartMs = json.optLong("trimStart", 0L),
                trimEndMs = json.optLong("trimEnd", 0L),
                crop = cropFromJson(json.optJSONObject("crop")),
            )
        } catch (_: Exception) {
            fallback
        }
    }

    fun runToJson(run: RunState): String {
        val root = JSONObject()
        root.put("schemaVersion", RUN_SCHEMA_VERSION)
        root.put("batchId", run.batchId)
        root.put("startedAt", run.startedAt)
        root.put("currentIndex", run.currentIndex)
        root.put("isRunning", run.isRunning)
        run.stopReason?.let { root.put("stopReason", it) }
        val items = JSONArray()
        run.items.forEach { item ->
            items.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("uri", item.uri)
                item.source?.let { put("source", sourceToJson(it)) }
                put("recipe", recipeToJson(item.recipe))
                put("status", item.status.name)
                put("progress", item.progress.toDouble())
                item.error?.let { put("error", it) }
                item.tempPath?.let { put("tempPath", it) }
                item.outputUri?.let { put("outputUri", it) }
                item.outputName?.let { put("outputName", it) }
                put("outputSize", item.outputSizeBytes)
                item.locationLabel?.let { put("location", it) }
                put("publishOnly", item.publishOnly)
                item.pendingUri?.let { put("pendingUri", it) }
                put("publishReady", item.publishReady)
            })
        }
        root.put("items", items)
        return root.toString()
    }

    fun runFromJson(raw: String?): RunState? {
        if (raw.isNullOrBlank()) return null
        val root = try { JSONObject(raw) } catch (e: Exception) {
            throw java.io.IOException("任务记录损坏，已保留原始记录", e)
        }
        val version = root.optInt("schemaVersion", 1)
        if (version !in 1..RUN_SCHEMA_VERSION) throw java.io.IOException("不支持的任务版本：$version")
        return try {
            val array = root.optJSONArray("items") ?: JSONArray()
            val parsed = ArrayList<RunItem>(array.length())
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                var status = runCatching { JobStatus.valueOf(item.getString("status")) }
                    .getOrDefault(JobStatus.Interrupted)
                if (status == JobStatus.Queued || status == JobStatus.Preparing ||
                        status == JobStatus.Encoding || status == JobStatus.Publishing
                ) {
                    status = JobStatus.Interrupted
                }
                parsed += RunItem(
                    id = item.getString("id"),
                    name = item.optString("name", "视频"),
                    uri = item.getString("uri"),
                    source = sourceFromJson(item.optJSONObject("source")),
                    recipe = recipeFromJson(item.optJSONObject("recipe")),
                    status = status,
                    progress = item.optDouble("progress", 0.0).toFloat(),
                    error = item.optString("error").takeIf { it.isNotBlank() && it != "null" },
                    tempPath = item.optString("tempPath").takeIf { it.isNotBlank() && it != "null" },
                    outputUri = item.optString("outputUri").takeIf { it.isNotBlank() && it != "null" },
                    outputName = item.optString("outputName").takeIf { it.isNotBlank() && it != "null" },
                    outputSizeBytes = item.optLong("outputSize", 0L),
                    locationLabel = item.optString("location").takeIf { it.isNotBlank() && it != "null" },
                    publishOnly = item.optBoolean("publishOnly", false),
                    pendingUri = item.optString("pendingUri").takeIf { it.isNotBlank() && it != "null" },
                    publishReady = item.optBoolean("publishReady", false),
                )
            }
            if (parsed.isEmpty()) return null
            RunState(
                batchId = root.optString("batchId", "run"),
                startedAt = root.optLong("startedAt", System.currentTimeMillis()),
                items = parsed,
                currentIndex = -1,
                isRunning = false,
                stopReason = root.optString("stopReason").takeIf { it.isNotBlank() && it != "null" },
            )
        } catch (e: Exception) {
            throw java.io.IOException("无法读取任务记录，已保留原始记录", e)
        }
    }
}
