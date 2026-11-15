package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class VaultThemePreset(val id: String, val displayName: String, val subtitle: String) {
    OBSIDIAN_NEON("obsidian_neon", "Obsidian Neon", "Pure black with neon blue & purple"),
    CYBER_VIOLET("cyber_violet", "Cyber Violet", "Deep slate with electric violet glow"),
    STEALTH_MINIMAL("stealth_minimal", "Stealth Minimal", "Low-profile monochrome dark")
}

@Immutable
data class VaultAccentPalette(
    val neonPrimary: Color,
    val neonSecondary: Color,
    val calcEqualStart: Color,
    val calcEqualEnd: Color,
    val cardBorder: Color,
    val keycapBg: Color,
    val keycapOperatorBg: Color,
    val statusGood: Color = NeonEmerald,
    val statusWarn: Color = NeonAmber,
    val statusDanger: Color = NeonRose
)

val LocalVaultAccents = staticCompositionLocalOf {
    VaultAccentPalette(
        neonPrimary = NeonBlue,
        neonSecondary = ElectricPurple,
        calcEqualStart = NeonBlue,
        calcEqualEnd = ElectricPurple,
        cardBorder = ObsidianBorder,
        keycapBg = ObsidianKeycap,
        keycapOperatorBg = ObsidianElevated
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun HideCalcTheme(
    themePreset: VaultThemePreset = VaultThemePreset.OBSIDIAN_NEON,
    content: @Composable () -> Unit
) {
    val accents = when (themePreset) {
        VaultThemePreset.OBSIDIAN_NEON -> VaultAccentPalette(
            neonPrimary = NeonBlue,
            neonSecondary = ElectricPurple,
            calcEqualStart = NeonBlue,
            calcEqualEnd = ElectricPurple,
            cardBorder = ObsidianBorder,
            keycapBg = ObsidianKeycap,
            keycapOperatorBg = ObsidianElevated
        )
        VaultThemePreset.CYBER_VIOLET -> VaultAccentPalette(
            neonPrimary = CyberVioletPrimary,
            neonSecondary = CyberCyanSecondary,
            calcEqualStart = CyberVioletPrimary,
            calcEqualEnd = ElectricViolet,
            cardBorder = Color(0xFF32264D),
            keycapBg = Color(0xFF131122),
            keycapOperatorBg = Color(0xFF1F1938)
        )
        VaultThemePreset.STEALTH_MINIMAL -> VaultAccentPalette(
            neonPrimary = StealthIcePrimary,
            neonSecondary = StealthAccentSecondary,
            calcEqualStart = Color(0xFF475569),
            calcEqualEnd = Color(0xFF334155),
            cardBorder = Color(0xFF222733),
            keycapBg = Color(0xFF111318),
            keycapOperatorBg = Color(0xFF1B1F27)
        )
    }

    val colorScheme = darkColorScheme(
        primary = accents.neonPrimary,
        onPrimary = ObsidianBlack,
        primaryContainer = accents.keycapOperatorBg,
        onPrimaryContainer = TextPrimary,
        secondary = accents.neonSecondary,
        onSecondary = TextPrimary,
        secondaryContainer = ObsidianCard,
        onSecondaryContainer = TextPrimary,
        tertiary = NeonEmerald,
        background = ObsidianBlack,
        onBackground = TextPrimary,
        surface = ObsidianSurface,
        onSurface = TextPrimary,
        surfaceVariant = ObsidianCard,
        onSurfaceVariant = TextSecondary,
        outline = accents.cardBorder,
        outlineVariant = Color(0xFF1B2234),
        error = NeonRose,
        onError = TextPrimary
    )

    CompositionLocalProvider(LocalVaultAccents provides accents) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
