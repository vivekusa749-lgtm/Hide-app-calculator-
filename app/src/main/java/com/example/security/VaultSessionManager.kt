package com.example.security

import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinates runtime unlock state, prevents auto-lock while system pickers or permission
 * settings screens are open, and wipes in-memory decrypted thumbnails on lock.
 */
class VaultSessionManager {
    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    @Volatile
    private var suppressAutoLockUntilMs: Long = 0L

    // In-memory thumbnail cache (approx. 16 MB max) cleared immediately when vault locks
    val thumbnailCache = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun unlockVault() {
        _isVaultUnlocked.value = true
    }

    fun lockVault() {
        _isVaultUnlocked.value = false
        thumbnailCache.evictAll()
    }

    /**
     * Marks that the user intentionally left the activity to pick media, save a backup,
     * or grant Android system permissions so returning doesn't immediately re-lock.
     */
    fun allowTemporaryExternalActivity(durationMs: Long = 120_000L) {
        suppressAutoLockUntilMs = System.currentTimeMillis() + durationMs
    }

    fun consumeOrShouldAutoLock(): Boolean {
        val now = System.currentTimeMillis()
        if (now <= suppressAutoLockUntilMs) {
            suppressAutoLockUntilMs = 0L
            return false
        }
        return true
    }
}
