package com.example.ui.vault

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.VaultMediaEntity
import com.example.data.VaultMediaRepository
import com.example.data.VaultMediaType
import com.example.data.VaultStorageSummary
import com.example.security.KeystoreCryptoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MediaActionFeedback(
    val isImporting: Boolean = false,
    val statusMessage: String? = null
)

class MediaVaultViewModel(
    private val repository: VaultMediaRepository,
    val cryptoManager: KeystoreCryptoManager
) : ViewModel() {

    val activePhotos: StateFlow<List<VaultMediaEntity>> = repository.observePhotos(trashed = false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trashedPhotos: StateFlow<List<VaultMediaEntity>> = repository.observePhotos(trashed = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeVideos: StateFlow<List<VaultMediaEntity>> = repository.observeVideos(trashed = false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trashedVideos: StateFlow<List<VaultMediaEntity>> = repository.observeVideos(trashed = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val storageSummary: StateFlow<VaultStorageSummary> = repository.observeStorageSummary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultStorageSummary())

    private val _feedback = MutableStateFlow(MediaActionFeedback())
    val feedback: StateFlow<MediaActionFeedback> = _feedback.asStateFlow()

    fun getEncryptedFile(entity: VaultMediaEntity) = repository.getEncryptedFile(entity)

    suspend fun loadThumbnail(entity: VaultMediaEntity): Bitmap? = repository.loadThumbnail(entity)

    suspend fun loadFullPhoto(entity: VaultMediaEntity): Bitmap? = repository.loadFullPhoto(entity)

    fun importPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _feedback.update { it.copy(isImporting = true, statusMessage = null) }
            val count = repository.importMediaUris(uris, VaultMediaType.PHOTO)
            _feedback.update {
                it.copy(
                    isImporting = false,
                    statusMessage = if (count > 0) {
                        "Encrypted and imported $count photo(s) into vault."
                    } else {
                        "Could not import selected photo(s)."
                    }
                )
            }
        }
    }

    fun importVideos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _feedback.update { it.copy(isImporting = true, statusMessage = null) }
            val count = repository.importMediaUris(uris, VaultMediaType.VIDEO)
            _feedback.update {
                it.copy(
                    isImporting = false,
                    statusMessage = if (count > 0) {
                        "Encrypted and imported $count video(s) into vault."
                    } else {
                        "Could not import selected video(s)."
                    }
                )
            }
        }
    }

    fun moveToTrash(ids: List<String>) {
        viewModelScope.launch {
            repository.moveToTrash(ids)
            _feedback.update { it.copy(statusMessage = "Moved ${ids.size} item(s) to Vault Trash.") }
        }
    }

    fun restoreFromTrash(ids: List<String>) {
        viewModelScope.launch {
            repository.restoreFromTrash(ids)
            _feedback.update { it.copy(statusMessage = "Restored ${ids.size} item(s) to vault.") }
        }
    }

    fun deletePermanently(items: List<VaultMediaEntity>) {
        viewModelScope.launch {
            repository.deletePermanently(items)
            _feedback.update { it.copy(statusMessage = "Permanently wiped ${items.size} encrypted item(s).") }
        }
    }

    fun exportToDeviceGallery(item: VaultMediaEntity) {
        viewModelScope.launch {
            val ok = repository.exportToDeviceGallery(item)
            _feedback.update {
                it.copy(
                    statusMessage = if (ok) {
                        "Restored decrypted copy to device gallery (${item.originalName})."
                    } else {
                        "Failed to export copy to device gallery."
                    }
                )
            }
        }
    }

    fun clearStatusMessage() {
        _feedback.update { it.copy(statusMessage = null) }
    }

    class Factory(
        private val repository: VaultMediaRepository,
        private val cryptoManager: KeystoreCryptoManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MediaVaultViewModel(repository, cryptoManager) as T
    }
}
