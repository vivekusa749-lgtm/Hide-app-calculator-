package com.example.data

import android.content.Context
import android.net.Uri
import com.example.security.KeystoreCryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
private data class BackupManifest(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val items: List<VaultMediaEntity>
)

/**
 * Streams portable, passphrase-encrypted `.hcxbackup` archives so backups can be
 * restored even on a new device without leaking unencrypted media to disk.
 */
class BackupRepository(
    private val context: Context,
    private val dao: VaultDao,
    private val mediaRepository: VaultMediaRepository,
    private val cryptoManager: KeystoreCryptoManager
) {
    companion object {
        private val BACKUP_STREAM_MAGIC = byteArrayOf('H'.code.toByte(), 'C'.code.toByte(), 'X'.code.toByte(), 'B'.code.toByte(), '2'.code.toByte())
        private const val SALT_LEN = 16
        private const val IV_LEN = 12
        private const val PBKDF2_ITERS = 40_000
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val secureRandom = SecureRandom()

    suspend fun exportEncryptedBackup(destUri: Uri, passphrase: String): Int = withContext(Dispatchers.IO) {
        require(passphrase.length >= 6) { "Backup passphrase must be at least 6 characters" }
        val items = dao.getAllMediaOnce()
        val manifest = BackupManifest(items = items)
        val manifestBytes = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)

        val salt = ByteArray(SALT_LEN).also { secureRandom.nextBytes(it) }
        val backupKey = deriveKey(passphrase, salt)

        context.contentResolver.openOutputStream(destUri)?.use { rawOut ->
            DataOutputStream(BufferedOutputStream(rawOut)).use { out ->
                out.write(BACKUP_STREAM_MAGIC)
                out.write(salt)

                // Write encrypted manifest
                val encryptedManifest = encryptBytes(manifestBytes, backupKey)
                out.writeInt(encryptedManifest.size)
                out.write(encryptedManifest)

                // Write each media item chunk-by-chunk re-encrypted under backupKey
                out.writeInt(items.size)
                for (item in items) {
                    val encFile = mediaRepository.getEncryptedFile(item)
                    val idBytes = item.id.toByteArray(Charsets.UTF_8)
                    out.writeInt(idBytes.size)
                    out.write(idBytes)

                    if (!encFile.exists()) {
                        out.writeLong(0L)
                        continue
                    }

                    val plainLen = cryptoManager.readPlaintextLength(encFile)
                    out.writeLong(plainLen)

                    val chunkSize = KeystoreCryptoManager.PLAINTEXT_CHUNK_SIZE
                    val chunkCount = ((plainLen + chunkSize - 1) / chunkSize).toInt()
                    out.writeInt(chunkCount)
                    for (i in 0 until chunkCount) {
                        val plainChunk = cryptoManager.decryptChunk(encFile, i, plainLen)
                        val cipherChunk = encryptBytes(plainChunk, backupKey)
                        out.writeInt(cipherChunk.size)
                        out.write(cipherChunk)
                    }
                }
                out.flush()
            }
        } ?: error("Could not open backup destination")

        items.size
    }

    suspend fun importEncryptedBackup(sourceUri: Uri, passphrase: String): Int = withContext(Dispatchers.IO) {
        require(passphrase.isNotEmpty()) { "Passphrase is required" }
        var restoredCount = 0

        context.contentResolver.openInputStream(sourceUri)?.use { rawIn ->
            DataInputStream(BufferedInputStream(rawIn)).use { input ->
                val magic = ByteArray(BACKUP_STREAM_MAGIC.size)
                input.readFully(magic)
                require(magic.contentEquals(BACKUP_STREAM_MAGIC)) { "Unsupported or corrupted backup file" }

                val salt = ByteArray(SALT_LEN)
                input.readFully(salt)
                val backupKey = deriveKey(passphrase, salt)

                val manifestLen = input.readInt()
                require(manifestLen in 1..(32 * 1024 * 1024)) { "Invalid backup manifest size" }
                val encryptedManifest = ByteArray(manifestLen)
                input.readFully(encryptedManifest)

                val manifestBytes = runCatching { decryptBytes(encryptedManifest, backupKey) }
                    .getOrElse { throw IllegalArgumentException("Incorrect backup passphrase or corrupted archive") }
                val manifest = json.decodeFromString<BackupManifest>(manifestBytes.toString(Charsets.UTF_8))
                val itemById = manifest.items.associateBy { it.id }

                val entryCount = input.readInt()
                repeat(entryCount) {
                    val idLen = input.readInt()
                    val idBytes = ByteArray(idLen)
                    input.readFully(idBytes)
                    val id = idBytes.toString(Charsets.UTF_8)
                    val plainLen = input.readLong()
                    if (plainLen <= 0L) return@repeat

                    val chunkCount = input.readInt()
                    val item = itemById[id] ?: return@repeat
                    val destFile = File(mediaRepository.vaultDir, item.encryptedFileName)

                    // Stream decrypted chunks straight into Keystore-backed chunk encryption
                    val pipeIn = object : java.io.InputStream() {
                        var currentChunk = 0
                        var currentBuffer = ByteArray(0)
                        var currentPos = 0

                        override fun read(): Int {
                            val single = ByteArray(1)
                            val count = read(single, 0, 1)
                            return if (count == -1) -1 else single[0].toInt() and 0xFF
                        }

                        override fun read(b: ByteArray, off: Int, len: Int): Int {
                            if (currentPos >= currentBuffer.size) {
                                if (currentChunk >= chunkCount) return -1
                                val encLen = input.readInt()
                                val encBytes = ByteArray(encLen)
                                input.readFully(encBytes)
                                currentBuffer = decryptBytes(encBytes, backupKey)
                                currentPos = 0
                                currentChunk++
                            }
                            val toCopy = minOf(len, currentBuffer.size - currentPos)
                            System.arraycopy(currentBuffer, currentPos, b, off, toCopy)
                            currentPos += toCopy
                            return toCopy
                        }
                    }

                    cryptoManager.encryptStreamToFile(pipeIn, destFile)
                    dao.insertMedia(item)
                    restoredCount++
                }
            }
        } ?: error("Could not open backup archive")

        restoredCount
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERS, 256)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun encryptBytes(plain: ByteArray, key: SecretKeySpec): ByteArray {
        val iv = ByteArray(IV_LEN).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val cipherText = cipher.doFinal(plain)
        return ByteArray(IV_LEN + cipherText.size).also {
            System.arraycopy(iv, 0, it, 0, IV_LEN)
            System.arraycopy(cipherText, 0, it, IV_LEN, cipherText.size)
        }
    }

    private fun decryptBytes(ivAndCipher: ByteArray, key: SecretKeySpec): ByteArray {
        val iv = ivAndCipher.copyOfRange(0, IV_LEN)
        val cipherText = ivAndCipher.copyOfRange(IV_LEN, ivAndCipher.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(cipherText)
    }
}
