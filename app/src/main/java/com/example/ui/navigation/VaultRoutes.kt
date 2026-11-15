package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface VaultTabRoute {
    @Serializable
    data object Dashboard : VaultTabRoute

    @Serializable
    data object Photos : VaultTabRoute

    @Serializable
    data object Videos : VaultTabRoute

    @Serializable
    data object AppLocker : VaultTabRoute

    @Serializable
    data object Settings : VaultTabRoute
}
