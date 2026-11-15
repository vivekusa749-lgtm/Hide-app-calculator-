package com.example.di

import android.content.Context
import com.example.applocker.AppLockerRepository
import com.example.data.BackupRepository
import com.example.data.VaultDao
import com.example.data.VaultDatabase
import com.example.data.VaultMediaRepository
import com.example.security.KeystoreCryptoManager
import com.example.security.SecurityPreferencesManager
import com.example.security.VaultSessionManager

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val cryptoManager: KeystoreCryptoManager by lazy { KeystoreCryptoManager() }
    val securityPrefs: SecurityPreferencesManager by lazy { SecurityPreferencesManager(appContext) }
    val sessionManager: VaultSessionManager by lazy { VaultSessionManager() }
    val database: VaultDatabase by lazy { VaultDatabase.create(appContext) }
    val vaultDao: VaultDao by lazy { database.vaultDao() }

    val mediaRepository: VaultMediaRepository by lazy {
        VaultMediaRepository(
            context = appContext,
            dao = vaultDao,
            cryptoManager = cryptoManager,
            sessionManager = sessionManager
        )
    }

    val appLockerRepository: AppLockerRepository by lazy {
        AppLockerRepository(
            context = appContext,
            dao = vaultDao
        )
    }

    val backupRepository: BackupRepository by lazy {
        BackupRepository(
            context = appContext,
            dao = vaultDao,
            mediaRepository = mediaRepository,
            cryptoManager = cryptoManager
        )
    }
}
