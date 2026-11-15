package com.example

import com.example.security.KeystoreCryptoManager
import com.example.security.PinHashHelper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultCryptoTest {

    @Test
    fun `pbkdf2 pin hashing and recovery key verification work accurately`() {
        val hashed = PinHashHelper.hashSecret("258099")
        assertTrue(PinHashHelper.verifySecret("258099", hashed.hashBase64, hashed.saltBase64))
        assertFalse(PinHashHelper.verifySecret("123456", hashed.hashBase64, hashed.saltBase64))

        val recoveryKey = PinHashHelper.generateRecoveryKey()
        assertEquals(19, recoveryKey.length)
        val hashedKey = PinHashHelper.hashSecret(PinHashHelper.normalizeRecoveryInput(recoveryKey))
        assertTrue(
            PinHashHelper.verifySecret(
                PinHashHelper.normalizeRecoveryInput(recoveryKey.lowercase()),
                hashedKey.hashBase64,
                hashedKey.saltBase64
            )
        )
    }

    @Test
    fun `chunked aes-gcm encryption supports multi-chunk random access decryption`() {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val crypto = KeystoreCryptoManager(testFallbackKey = keyGen.generateKey())

        // 150 KB spans 3 chunks (64 KB + 64 KB + 22 KB)
        val payload = ByteArray(150 * 1024) { idx -> ((idx * 31) and 0xFF).toByte() }
        val tempFile = File.createTempFile("hcx_test_", ".hcx")
        try {
            val writtenPlainLen = crypto.encryptStreamToFile(ByteArrayInputStream(payload), tempFile)
            assertEquals(payload.size.toLong(), writtenPlainLen)
            assertEquals(payload.size.toLong(), crypto.readPlaintextLength(tempFile))

            // Verify random-access decryption of last chunk (chunk 2)
            val chunk2 = crypto.decryptChunk(tempFile, 2, writtenPlainLen)
            val expectedChunk2 = payload.copyOfRange(2 * 64 * 1024, payload.size)
            assertArrayEquals(expectedChunk2, chunk2)

            // Verify full round-trip
            val roundTrip = crypto.decryptFileToByteArray(tempFile)
            assertArrayEquals(payload, roundTrip)
        } finally {
            tempFile.delete()
        }
    }
}
