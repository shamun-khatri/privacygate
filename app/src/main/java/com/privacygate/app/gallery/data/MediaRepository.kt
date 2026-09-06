package com.privacygate.app.gallery.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import com.privacygate.app.gallery.model.GalleryPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepository(private val context: Context) {

    suspend fun fetchGalleryPhotos(): List<GalleryPhoto> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<GalleryPhoto>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val bucketCol = c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                while (c.moveToNext()) {
                    try {
                        val id = c.getLong(idCol)
                        val name = if (!c.isNull(nameCol)) c.getString(nameCol) else "IMG_$id"
                        val dateAdded = if (!c.isNull(dateCol)) c.getLong(dateCol) else 0L
                        val size = if (!c.isNull(sizeCol)) c.getLong(sizeCol) else 0L
                        val bucketName = if (bucketCol != -1 && !c.isNull(bucketCol)) c.getString(bucketCol) else null
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )

                        photos.add(
                            GalleryPhoto(
                                id = id,
                                uri = contentUri,
                                name = name,
                                dateAdded = dateAdded,
                                size = size,
                                bucketName = bucketName
                            )
                        )
                    } catch (rowEx: Exception) {
                        android.util.Log.w("PrivacyGate", "Skipping bad row in MediaStore", rowEx)
                    }
                }
            }
            android.util.Log.i("PrivacyGate", "fetchGalleryPhotos loaded ${photos.size} photos from MediaStore")
        } catch (e: Exception) {
            android.util.Log.e("PrivacyGate", "Error querying MediaStore", e)
        }
        photos
    }

    suspend fun loadDownscaledBitmap(uri: Uri, maxDimension: Int = 1024): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // First decode bounds
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            var inSampleSize = 1
            if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / inSampleSize >= maxDimension && halfWidth / inSampleSize >= maxDimension) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
