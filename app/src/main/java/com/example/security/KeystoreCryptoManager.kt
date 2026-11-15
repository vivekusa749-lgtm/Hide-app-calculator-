package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hardware-backed Android Keystore AES-256-GCM encryption engine using fixed-size
 * 64 KB chunks so large photos and videos can be encrypted, decrypted, and randomly
 * seeked in memory without writing plaintext temporary files to disk.
 *
 * File format (HCX1):
 * - Magic (4 bytes): "HCX1"
 * - Original plaintext length (8 bytes, big-endian Long)
 * - Repeated chunks of up to [PLAINTEXT_CHUNK_SIZE] (65,536 bytes):
 *   - 12-byte random IV
 *   - AES-GCM ciphertext + 16-byte authentication tag
 */
class KeystoreCryptoManager(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val testFallbackKey: SecretKey? = null
) {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_KEY_ALIAS = "hide_calc_x_vault_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        const val IV_SIZE = 12
        const val TAG_SIZE = 16
        const val PLAINTEXT_CHUNK_SIZE = 64 * 1024
        const val ENCRYPTED_CHUNK_STRIDE = IV_SIZE + PLAINTEXT_CHUNK_SIZE + TAG_SIZE
        val MAGIC = byteArrayOf('H'.code.toByte(), 'C'.code.toByte(), 'X'.code.toByte(), '1'.code.toByte())
        const val HEADER_SIZE = 4 + 8
        private const val BACKUP_MAGIC = "HCXB"
        private const val BACKUP_ITERATIONS = 50_000
    }

    private val secureRandom = SecureRandom()

    fun getOrCreateMasterKey(): SecretKey {
        if (testFallbackKey != null) return testFallbackKey
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(false)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    fun isKeystoreReady(): Boolean = runCatching {
        getOrCreateMasterKey()
        true
    }.getOrDefault(false)

    /**
     * Encrypts [input] stream into [destFile] in chunked HCX1 format and returns plaintext byte count.
     */
    fun encryptStreamToFile(input: InputStream, destFile: File): Long {
        val key = getOrCreateMasterKey()
        val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")
        var totalPlaintextBytes = 0L

        RandomAccessFile(tempFile, "rw").use { raf ->
            raf.setLength(0)
            // Placeholder header; total length written once stream finishes
            raf.write(MAGIC)
            raf.writeLong(0L)

            val buffer = ByteArray(PLAINTEXT_CHUNK_SIZE)
            while (true) {
                var readTotal = 0
                while (readTotal < PLAINTEXT_CHUNK_SIZE) {
                    val read = input.read(buffer, readTotal, PLAINTEXT_CHUNK_SIZE - readTotal)
                    if (read == -1) break
                    readTotal += read
                }
                if (readTotal <= 0) break

                val iv = ByteArray(IV_SIZE).also { secureRandom.nextBytes(it) }
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                val cipherBytes = cipher.doFinal(buffer, 0, readTotal)

                raf.write(iv)
                raf.write(cipherBytes)
                totalPlaintextBytes += readTotal
            }

            raf.seek(MAGIC.size.toLong())
            raf.writeLong(totalPlaintextBytes)
        }

        if (destFile.exists()) destFile.delete()
        if (!tempFile.renameTo(destFile)) {
            tempFile.copyTo(destFile, overwrite = true)
            tempFile.delete()
        }
        return totalPlaintextBytes
    }

    fun readPlaintextLength(encryptedFile: File): Long {
        RandomAccessFile(encryptedFile, "r").use { raf ->
            if (raf.length() < HEADER_SIZE) return 0L
            val magic = ByteArray(MAGIC.size)
            raf.readFully(magic)
            require(magic.contentEquals(MAGIC)) { "Invalid vault file header" }
            return raf.readLong()
        }
    }

    fun decryptChunk(encryptedFile: File, chunkIndex: Int, plaintextLength: Long): ByteArray {
        val key = getOrCreateMasterKey()
        val chunkOffsetInPlaintext = chunkIndex.toLong() * PLAINTEXT_CHUNK_SIZE
        val remaining = (plaintextLength - chunkOffsetInPlaintext).coerceAtLeast(0L)
        if (remaining == 0L) return ByteArray(0)
        val expectedPlainLen = minOf(PLAINTEXT_CHUNK_SIZE.toLong(), remaining).toInt()
        val expectedCipherLen = expectedPlainLen + TAG_SIZE

        val fileOffset = HEADER_SIZE.toLong() + chunkIndex.toLong() * ENCRYPTED_CHUNK_STRIDE
        val iv = ByteArray(IV_SIZE)
        val cipherBytes = ByteArray(expectedCipherLen)

        RandomAccessFile(encryptedFile, "r").use { raf ->
            raf.seek(fileOffset)
            raf.readFully(iv)
            raf.readFully(cipherBytes)
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherBytes)
    }

    fun decryptFileToByteArray(encryptedFile: File): ByteArray {
        val totalLen = readPlaintextLength(encryptedFile)
        val out = ByteArrayOutputStream(totalLen.toInt().coerceAtLeast(0))
        val chunkCount = ((totalLen + PLAINTEXT_CHUNK_SIZE - 1) / PLAINTEXT_CHUNK_SIZE).toInt()
        for (i in 0 until chunkCount) {
            out.write(decryptChunk(encryptedFile, i, totalLen))
        }
        return out.toByteArray()
    }

    fun decryptFileToOutputStream(encryptedFile: File, output: OutputStream) {
        val totalLen = readPlaintextLength(encryptedFile)
        val chunkCount = ((totalLen + PLAINTEXT_CHUNK_SIZE - 1) / PLAINTEXT_CHUNK_SIZE).toInt()
        for (i in 0 until chunkCount) {
            output.write(decryptChunk(encryptedFile, i, totalLen))
        }
        output.flush()
    }

    /**
     * Encrypts an archive byte payload with a user passphrase using PBKDF2 + AES-256-GCM.
     */
    fun encryptBackupPayload(payload: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { secureRandom.nextBytes(it) }
        val key = deriveBackupKey(passphrase, salt)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(payload)

        val magicBytes = BACKUP_MAGIC.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(magicBytes.size + salt.size + iv.size + encrypted.size)
            .put(magicBytes)
            .put(salt)
            .put(iv)
            .put(encrypted)
            .array()
    }

    fun decryptBackupPayload(archiveBytes: ByteArray, passphrase: String): ByteArray {
        val magicBytes = BACKUP_MAGIC.toByteArray(Charsets.UTF_8)
        require(archiveBytes.size > magicBytes.size + 16 + IV_SIZE + TAG_SIZE) {
            "Invalid backup archive"
        }
        val header = archiveBytes.copyOfRange(0, magicBytes.size)
        require(header.contentEquals(magicBytes)) { "Unsupported backup file format" }

        val saltStart = magicBytes.size
        val ivStart = saltStart + 16
        val dataStart = ivStart + IV_SIZE
        val salt = archiveBytes.copyOfRange(saltStart, ivStart)
        val iv = archiveBytes.copyOfRange(ivStart, dataStart)
        val ciphertext = archiveBytes.copyOfRange(dataStart, archiveBytes.size)

        val key = deriveBackupKey(passphrase, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun deriveBackupKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, BACKUP_ITERATIONS, 256)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
