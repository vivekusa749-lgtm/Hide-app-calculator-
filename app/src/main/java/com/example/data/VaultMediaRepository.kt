package com.example.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.media.EncryptedMediaDataSource
import com.example.security.KeystoreCryptoManager
import com.example.security.VaultSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class VaultStorageSummary(
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val photoBytes: Long = 0L,
    val videoBytes: Long = 0L,
    val deviceFreeBytes: Long = 0L,
    val deviceTotalBytes: Long = 0L
) {
    val totalVaultBytes: Long get() = photoBytes + videoBytes
}

class VaultMediaRepository(
    private val context: Context,
    private val dao: VaultDao,
    private val cryptoManager: KeystoreCryptoManager,
    private val sessionManager: VaultSessionManager
) {
    val vaultDir: File = File(context.filesDir, "vault_media").apply { mkdirs() }

    fun observePhotos(trashed: Boolean): Flow<List<VaultMediaEntity>> =
        if (trashed) dao.observeTrashedMedia(VaultMediaType.PHOTO)
        else dao.observeActiveMedia(VaultMediaType.PHOTO)

    fun observeVideos(trashed: Boolean): Flow<List<VaultMediaEntity>> =
        if (trashed) dao.observeTrashedMedia(VaultMediaType.VIDEO)
        else dao.observeActiveMedia(VaultMediaType.VIDEO)

    fun observeStorageSummary(): Flow<VaultStorageSummary> = combine(
        dao.observeActiveCountByType(VaultMediaType.PHOTO),
        dao.observeActiveCountByType(VaultMediaType.VIDEO),
        dao.observeTotalBytesByType(VaultMediaType.PHOTO),
        dao.observeTotalBytesByType(VaultMediaType.VIDEO)
    ) { photoCount, videoCount, photoBytes, videoBytes ->
        val stat = StatFs(context.filesDir.absolutePath)
        VaultStorageSummary(
            photoCount = photoCount,
            videoCount = videoCount,
            photoBytes = photoBytes,
            videoBytes = videoBytes,
            deviceFreeBytes = stat.availableBytes,
            deviceTotalBytes = stat.totalBytes
        )
    }

    fun getEncryptedFile(entity: VaultMediaEntity): File = File(vaultDir, entity.encryptedFileName)

    suspend fun importMediaUris(uris: List<Uri>, type: VaultMediaType): Int = withContext(Dispatchers.IO) {
        var imported = 0
        for (uri in uris) {
            val result = runCatching {
                val id = UUID.randomUUID().toString()
                val encryptedName = "$id.hcx"
                val destFile = File(vaultDir, encryptedName)
                val (displayName, mimeType) = queryDisplayNameAndMime(uri, type)

                val plainBytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    cryptoManager.encryptStreamToFile(input, destFile)
                } ?: error("Unable to open selected file")

                var width = 0
                var height = 0
                var durationMs = 0L

                if (type == VaultMediaType.PHOTO) {
                    val bytes = cryptoManager.decryptFileToByteArray(destFile)
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    width = opts.outWidth.coerceAtLeast(0)
                    height = opts.outHeight.coerceAtLeast(0)
                } else {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(EncryptedMediaDataSource(destFile, cryptoManager))
                        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull() ?: 0L
                        width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                            ?.toIntOrNull() ?: 0
                        height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                            ?.toIntOrNull() ?: 0
                    } catch (_: Exception) {
                        // Video metadata extraction may vary by codec
                    } finally {
                        retriever.release()
                    }
                }

                dao.insertMedia(
                    VaultMediaEntity(
                        id = id,
                        originalName = displayName,
                        mimeType = mimeType,
                        mediaType = type,
                        encryptedFileName = encryptedName,
                        sizeBytes = plainBytes,
                        durationMs = durationMs,
                        width = width,
                        height = height
                    )
                )
            }
            if (result.isSuccess) imported++
        }
        imported
    }

    suspend fun loadThumbnail(entity: VaultMediaEntity, maxDimPx: Int = 360): Bitmap? {
        sessionManager.thumbnailCache.get(entity.id)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = getEncryptedFile(entity)
                if (!file.exists()) return@runCatching null
                val bitmap = if (entity.mediaType == VaultMediaType.PHOTO) {
                    val raw = cryptoManager.decryptFileToByteArray(file)
                    decodeSampledBitmap(raw, maxDimPx, maxDimPx)
                } else {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(EncryptedMediaDataSource(file, cryptoManager))
                        val frame = retriever.getFrameAtTime(
                            500_000L,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        ) ?: retriever.frameAtTime
                        frame?.let { scaleDownBitmap(it, maxDimPx) }
                    } finally {
                        retriever.release()
                    }
                }
                if (bitmap != null) {
                    sessionManager.thumbnailCache.put(entity.id, bitmap)
                }
                bitmap
            }.getOrNull()
        }
    }

    suspend fun loadFullPhoto(entity: VaultMediaEntity, maxDimPx: Int = 2048): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = getEncryptedFile(entity)
                if (!file.exists()) return@runCatching null
                val raw = cryptoManager.decryptFileToByteArray(file)
                decodeSampledBitmap(raw, maxDimPx, maxDimPx)
            }.getOrNull()
        }

    suspend fun moveToTrash(ids: List<String>) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) dao.markTrashed(ids)
    }

    suspend fun restoreFromTrash(ids: List<String>) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) dao.restoreFromTrash(ids)
    }

    suspend fun deletePermanently(items: List<VaultMediaEntity>) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        for (item in items) {
            sessionManager.thumbnailCache.remove(item.id)
            val file = getEncryptedFile(item)
            if (file.exists()) {
                file.delete()
            }
        }
        dao.deleteMediaByIds(items.map { it.id })
    }

    /**
     * Exports a decrypted copy back to the device Gallery (Pictures/Movies) via MediaStore.
     */
    suspend fun exportToDeviceGallery(entity: VaultMediaEntity): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val file = getEncryptedFile(entity)
            if (!file.exists()) return@runCatching false
            val resolver = context.contentResolver
            val isPhoto = entity.mediaType == VaultMediaType.PHOTO
            val collection = if (isPhoto) {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, entity.originalName)
                put(MediaStore.MediaColumns.MIME_TYPE, entity.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val dir = if (isPhoto) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/HideCalculatorX")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val destUri = resolver.insert(collection, values) ?: return@runCatching false
            resolver.openOutputStream(destUri)?.use { out ->
                cryptoManager.decryptFileToOutputStream(file, out)
            } ?: return@runCatching false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val clearPending = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(destUri, clearPending, null, null)
            }
            true
        }.getOrDefault(false)
    }

    private fun queryDisplayNameAndMime(uri: Uri, type: VaultMediaType): Pair<String, String> {
        val defaultMime = if (type == VaultMediaType.PHOTO) "image/jpeg" else "video/mp4"
        val mime = context.contentResolver.getType(uri) ?: defaultMime
        var name: String? = null
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
        val fallbackExt = if (type == VaultMediaType.PHOTO) "jpg" else "mp4"
        return (name ?: "vault_${System.currentTimeMillis()}.$fallbackExt") to mime
    }

    private fun decodeSampledBitmap(data: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, options)
        var inSampleSize = 1
        val (height, width) = options.outHeight to options.outWidth
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        val decodeOpts = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        return BitmapFactory.decodeByteArray(data, 0, data.size, decodeOpts)
    }

    private fun scaleDownBitmap(source: Bitmap, maxDim: Int): Bitmap {
        val maxSide = maxOf(source.width, source.height)
        if (maxSide <= maxDim) return source
        val scale = maxDim.toFloat() / maxSide.toFloat()
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true).also {
            if (it != source) source.recycle()
        }
    }
}
