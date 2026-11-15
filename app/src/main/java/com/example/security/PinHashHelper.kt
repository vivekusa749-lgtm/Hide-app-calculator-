package com.example.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinHashHelper {
    private const val ITERATIONS = 10_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private val secureRandom = SecureRandom()

    data class HashedSecret(val hashBase64: String, val saltBase64: String)

    fun hashSecret(secret: String): HashedSecret {
        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val hash = pbkdf2(secret.toCharArray(), salt)
        return HashedSecret(
            hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP),
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        )
    }

    fun verifySecret(candidate: String, storedHashBase64: String?, storedSaltBase64: String?): Boolean {
        if (storedHashBase64.isNullOrBlank() || storedSaltBase64.isNullOrBlank()) return false
        return try {
            val salt = Base64.decode(storedSaltBase64, Base64.NO_WRAP)
            val expectedHash = Base64.decode(storedHashBase64, Base64.NO_WRAP)
            val candidateHash = pbkdf2(candidate.toCharArray(), salt)
            MessageDigest.isEqual(candidateHash, expectedHash)
        } catch (_: Exception) {
            false
        }
    }

    fun generateRecoveryKey(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val groups = (0 until 4).map {
            buildString {
                repeat(4) {
                    append(alphabet[secureRandom.nextInt(alphabet.length)])
                }
            }
        }
        return groups.joinToString("-")
    }

    fun normalizeRecoveryInput(input: String): String =
        input.trim().uppercase().replace(" ", "")

    private fun pbkdf2(chars: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(chars, salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
