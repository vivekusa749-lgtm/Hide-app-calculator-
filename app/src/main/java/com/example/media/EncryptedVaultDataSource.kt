package com.example.media

import android.media.MediaDataSource
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.example.security.KeystoreCryptoManager
import java.io.File
import kotlin.math.min

/**
 * Custom Media3 [DataSource] that performs random-access chunked AES-256-GCM decryption
 * directly from encrypted vault files in memory, so decrypted video data never hits disk.
 */
@UnstableApi
class EncryptedVaultDataSource(
    private val cryptoManager: KeystoreCryptoManager,
    private val resolveFile: (Uri) -> File
) : BaseDataSource(/* isNetwork = */ false) {

    private var activeFile: File? = null
    private var currentUri: Uri? = null
    private var totalPlaintextLength: Long = 0L
    private var position: Long = 0L
    private var bytesRemaining: Long = 0L
    private var opened = false

    private var cachedChunkIndex = -1
    private var cachedChunkBytes: ByteArray = ByteArray(0)

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val uri = dataSpec.uri
        val file = resolveFile(uri)
        require(file.exists()) { "Encrypted media file missing" }

        activeFile = file
        currentUri = uri
        totalPlaintextLength = cryptoManager.readPlaintextLength(file)
        position = dataSpec.position.coerceIn(0L, totalPlaintextLength)

        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            min(dataSpec.length, totalPlaintextLength - position)
        } else {
            totalPlaintextLength - position
        }

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining <= 0L) return C.RESULT_END_OF_INPUT
        val file = activeFile ?: return C.RESULT_END_OF_INPUT

        val chunkSize = KeystoreCryptoManager.PLAINTEXT_CHUNK_SIZE
        val chunkIndex = (position / chunkSize).toInt()
        val offsetInChunk = (position % chunkSize).toInt()

        if (chunkIndex != cachedChunkIndex) {
            cachedChunkBytes = cryptoManager.decryptChunk(file, chunkIndex, totalPlaintextLength)
            cachedChunkIndex = chunkIndex
        }

        val availableInChunk = cachedChunkBytes.size - offsetInChunk
        if (availableInChunk <= 0) return C.RESULT_END_OF_INPUT

        val toCopy = min(length.toLong(), min(bytesRemaining, availableInChunk.toLong())).toInt()
        System.arraycopy(cachedChunkBytes, offsetInChunk, buffer, offset, toCopy)

        position += toCopy
        bytesRemaining -= toCopy
        bytesTransferred(toCopy)
        return toCopy
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        currentUri = null
        activeFile = null
        cachedChunkIndex = -1
        cachedChunkBytes = ByteArray(0)
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory(
        private val cryptoManager: KeystoreCryptoManager,
        private val resolveFile: (Uri) -> File
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            EncryptedVaultDataSource(cryptoManager, resolveFile)
    }
}

/**
 * Android [MediaDataSource] for extracting encrypted video metadata and thumbnails
 * using [android.media.MediaMetadataRetriever] without creating plaintext temp files.
 */
class EncryptedMediaDataSource(
    private val file: File,
    private val cryptoManager: KeystoreCryptoManager
) : MediaDataSource() {

    private val totalLength: Long by lazy { cryptoManager.readPlaintextLength(file) }
    private var cachedChunkIndex = -1
    private var cachedChunkBytes = ByteArray(0)

    override fun getSize(): Long = totalLength

    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position < 0 || position >= totalLength) return -1
        val chunkSize = KeystoreCryptoManager.PLAINTEXT_CHUNK_SIZE
        val chunkIndex = (position / chunkSize).toInt()
        val offsetInChunk = (position % chunkSize).toInt()

        if (chunkIndex != cachedChunkIndex) {
            cachedChunkBytes = cryptoManager.decryptChunk(file, chunkIndex, totalLength)
            cachedChunkIndex = chunkIndex
        }

        val available = cachedChunkBytes.size - offsetInChunk
        if (available <= 0) return -1
        val toRead = min(size, available)
        System.arraycopy(cachedChunkBytes, offsetInChunk, buffer, offset, toRead)
        return toRead
    }

    override fun close() {
        cachedChunkIndex = -1
        cachedChunkBytes = ByteArray(0)
    }
}
