package com.privacygate.app.gallery.data

import android.content.Context
import com.privacygate.app.ai.gemma.GemmaEnrichment
import com.privacygate.app.gallery.model.GalleryPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CachedPhotoMeta(
    val id: Long,
    val labels: List<String>,
    val faceCount: Int,
    val hasPerson: Boolean,
    val isPortrait: Boolean,
    val isSelfie: Boolean,
    val isSensitiveDocument: Boolean,
    val documentType: String?,
    val segments: Set<String>,
    val gemmaEnrichment: GemmaEnrichment?,
    val gemmaLatencyMs: Long?
)

class PhotoIndexCache(private val context: Context) {

    private val cacheFile by lazy {
        File(context.filesDir, "photo_index_cache.json")
    }

    suspend fun loadCache(): Map<Long, CachedPhotoMeta> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<Long, CachedPhotoMeta>()
        if (!cacheFile.exists()) return@withContext result

        try {
            val jsonStr = cacheFile.readText()
            val root = JSONObject(jsonStr)
            val photosArray = root.optJSONArray("photos") ?: JSONArray()

            for (i in 0 until photosArray.length()) {
                val obj = photosArray.getJSONObject(i)
                val id = obj.getLong("id")

                val meta = CachedPhotoMeta(
                    id = id,
                    labels = obj.stringList("labels"),
                    faceCount = obj.optInt("faceCount", 0),
                    hasPerson = obj.optBoolean("hasPerson", false),
                    isPortrait = obj.optBoolean("isPortrait", false),
                    isSelfie = obj.optBoolean("isSelfie", false),
                    isSensitiveDocument = obj.optBoolean("isSensitiveDocument", false),
                    documentType = obj.nullableString("documentType"),
                    segments = obj.stringList("segments").toSet(),
                    gemmaEnrichment = obj.optJSONObject("gemma")?.toGemmaEnrichment(),
                    gemmaLatencyMs = obj.optLong("gemmaLatencyMs").takeIf { obj.has("gemmaLatencyMs") }
                )
                result[id] = meta
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }

    suspend fun saveCache(items: Collection<GalleryPhoto>) = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()
            val photosArray = JSONArray()

            for (p in items) {
                if (!p.isIndexed) continue
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("labels", JSONArray(p.labels))
                    put("faceCount", p.faceCount)
                    put("hasPerson", p.hasPerson)
                    put("isPortrait", p.isPortrait)
                    put("isSelfie", p.isSelfie)
                    put("isSensitiveDocument", p.isSensitiveDocument)
                    if (p.documentType != null) {
                        put("documentType", p.documentType)
                    }
                    put("segments", JSONArray(p.segments.toList()))
                    p.gemmaEnrichment?.let { put("gemma", it.toJson()) }
                    p.gemmaLatencyMs?.let { put("gemmaLatencyMs", it) }
                }
                photosArray.put(obj)
            }

            root.put("version", 2)
            root.put("updatedAt", System.currentTimeMillis())
            root.put("photos", photosArray)

            cacheFile.writeText(root.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun JSONObject.toGemmaEnrichment() = GemmaEnrichment(
        caption = optString("caption", ""),
        objects = stringList("objects"),
        scenes = stringList("scenes"),
        activities = stringList("activities"),
        searchLabels = stringList("searchLabels"),
        privacyCues = stringList("privacyCues"),
        vehiclePresent = optBoolean("vehiclePresent", false),
        registrationPlateVisible = optBoolean("registrationPlateVisible", false),
        confirmedMlKitLabels = stringList("confirmedMlKitLabels"),
        addedLabels = stringList("addedLabels"),
        conflictingLabels = stringList("conflictingLabels"),
        needsReview = optBoolean("needsReview", false),
        modelVersion = optString("modelVersion", GemmaEnrichment.MODEL_VERSION),
        analyzedAtEpochMs = optLong("analyzedAtEpochMs", 0L)
    )

    private fun GemmaEnrichment.toJson() = JSONObject().apply {
        put("caption", caption)
        put("objects", JSONArray(objects))
        put("scenes", JSONArray(scenes))
        put("activities", JSONArray(activities))
        put("searchLabels", JSONArray(searchLabels))
        put("privacyCues", JSONArray(privacyCues))
        put("vehiclePresent", vehiclePresent)
        put("registrationPlateVisible", registrationPlateVisible)
        put("confirmedMlKitLabels", JSONArray(confirmedMlKitLabels))
        put("addedLabels", JSONArray(addedLabels))
        put("conflictingLabels", JSONArray(conflictingLabels))
        put("needsReview", needsReview)
        put("modelVersion", modelVersion)
        put("analyzedAtEpochMs", analyzedAtEpochMs)
    }

    private fun JSONObject.stringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun JSONObject.nullableString(key: String): String? =
        optString(key, "").takeIf { it.isNotBlank() }
}
