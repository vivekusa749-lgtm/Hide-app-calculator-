package com.example.ui.applocker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.applocker.AppLockerPermissionsState
import com.example.applocker.AppLockerRepository
import com.example.applocker.InstalledAppItem
import com.example.data.LockedAppEntity
import com.example.security.SecurityPreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppLockerUiState(
    val apps: List<InstalledAppItem> = emptyList(),
    val lockedPackageNames: Set<String> = emptySet(),
    val isLoadingApps: Boolean = true,
    val searchQuery: String = "",
    val showSystemApps: Boolean = false,
    val permissions: AppLockerPermissionsState = AppLockerPermissionsState(),
    val isProtectionEnabled: Boolean = false
)

class AppLockerViewModel(
    private val appLockerRepository: AppLockerRepository,
    private val securityPrefs: SecurityPreferencesManager
) : ViewModel() {

    private val _apps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _searchQuery = MutableStateFlow("")
    private val _showSystem = MutableStateFlow(false)
    private val _permissions = MutableStateFlow(appLockerRepository.checkPermissions())

    val uiState: StateFlow<AppLockerUiState> = combine(
        _apps,
        appLockerRepository.observeLockedApps(),
        _isLoading,
        _searchQuery,
        _showSystem,
        _permissions,
        securityPrefs.settingsFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val apps = values[0] as List<InstalledAppItem>
        @Suppress("UNCHECKED_CAST")
        val lockedList = values[1] as List<LockedAppEntity>
        val loading = values[2] as Boolean
        val query = values[3] as String
        val showSys = values[4] as Boolean
        val perms = values[5] as AppLockerPermissionsState
        val settings = values[6] as com.example.security.SecuritySettingsState

        val filtered = apps.filter { app ->
            (showSys || !app.isSystemApp) &&
                (query.isBlank() || app.appName.contains(query, ignoreCase = true))
        }
        AppLockerUiState(
            apps = filtered,
            lockedPackageNames = lockedList.filter { it.isLocked }.map { it.packageName }.toSet(),
            isLoadingApps = loading,
            searchQuery = query,
            showSystemApps = showSys,
            permissions = perms,
            isProtectionEnabled = settings.appLockerEnabled
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLockerUiState())

    init {
        refreshAll()
    }

    fun refreshAll() {
        _permissions.value = appLockerRepository.checkPermissions()
        viewModelScope.launch {
            _isLoading.value = true
            _apps.value = appLockerRepository.getLaunchableApps()
            _isLoading.value = false
        }
    }

    fun refreshPermissions() {
        _permissions.value = appLockerRepository.checkPermissions()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleShowSystemApps() {
        _showSystem.update { !it }
    }

    fun toggleAppLock(app: InstalledAppItem, lock: Boolean) {
        viewModelScope.launch {
            appLockerRepository.setAppLocked(app, lock)
        }
    }

    fun setProtectionEnabled(enabled: Boolean) {
        val perms = appLockerRepository.checkPermissions()
        _permissions.value = perms
        viewModelScope.launch {
            if (enabled && !perms.allCorePermissionsGranted) {
                securityPrefs.setAppLockerEnabled(false)
                return@launch
            }
            securityPrefs.setAppLockerEnabled(enabled)
            if (enabled) {
                appLockerRepository.startMonitorService()
            } else {
                appLockerRepository.stopMonitorService()
            }
        }
    }

    class Factory(
        private val appLockerRepository: AppLockerRepository,
        private val securityPrefs: SecurityPreferencesManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppLockerViewModel(appLockerRepository, securityPrefs) as T
    }
}
