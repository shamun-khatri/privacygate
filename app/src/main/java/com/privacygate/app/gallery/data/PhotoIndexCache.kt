package com.privacygate.app.gallery.data

import android.content.Context
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
    val extractedText: String,
    val segments: Set<String>
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

                val labelsList = mutableListOf<String>()
                val labelsArr = obj.optJSONArray("labels")
                if (labelsArr != null) {
                    for (j in 0 until labelsArr.length()) {
                        labelsList.add(labelsArr.getString(j))
                    }
                }

                val segmentsSet = mutableSetOf<String>()
                val segArr = obj.optJSONArray("segments")
                if (segArr != null) {
                    for (j in 0 until segArr.length()) {
                        segmentsSet.add(segArr.getString(j))
                    }
                }

                val meta = CachedPhotoMeta(
                    id = id,
                    labels = labelsList,
                    faceCount = obj.optInt("faceCount", 0),
                    hasPerson = obj.optBoolean("hasPerson", false),
                    isPortrait = obj.optBoolean("isPortrait", false),
                    isSelfie = obj.optBoolean("isSelfie", false),
                    isSensitiveDocument = obj.optBoolean("isSensitiveDocument", false),
                    documentType = if (obj.has("documentType")) obj.getString("documentType") else null,
                    extractedText = obj.optString("extractedText", ""),
                    segments = segmentsSet
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
                    put("extractedText", p.extractedText.take(500))
                    put("segments", JSONArray(p.segments.toList()))
                }
                photosArray.put(obj)
            }

            root.put("version", 1)
            root.put("updatedAt", System.currentTimeMillis())
            root.put("photos", photosArray)

            cacheFile.writeText(root.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
