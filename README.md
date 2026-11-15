# Hide Calculator X

A native Android application built with Kotlin, Jetpack Compose, Material Design 3, Android Keystore AES-256-GCM encryption, Room, DataStore, and Media3 ExoPlayer.

## Core Features

1. **Discreet Calculator & Secret PIN Access**
   - Full `BigDecimal` calculator supporting `+`, `−`, `×`, `÷`, `%`, `+/-`, decimals, `AC`, and backspace.
   - Entering the secret PIN and pressing `=` unlocks the encrypted vault.
   - Optional **Biometric Fingerprint Unlock** (enabled in Settings): long-press `=` on the calculator or tap Fingerprint on the App Locker gate.
   - Long-pressing `AC` opens the Forgot PIN recovery dialog (Security Question or 16-character Offline Recovery Key).

2. **Encrypted Photo Vault**
   - Multi-photo import via zero-permission Android Photo Picker (`PickMultipleVisualMedia`).
   - Chunked AES-256-GCM encryption protected by `AndroidKeyStore`.
   - In-memory thumbnail cache wiped immediately upon vault lock.
   - Pinch-to-zoom full-screen viewer, Trash/Restore, Permanent Wipe, and Export to device Pictures.

3. **Encrypted Video Vault**
   - Multi-video import via Android Photo Picker into encrypted private storage.
   - In-app **Media3 ExoPlayer** streaming directly from AES-256-GCM chunks in memory (`EncryptedVaultDataSource`) with zero plaintext temporary files on disk.

4. **App Locker**
   - Lists launchable installed apps via targeted `<queries>` (`ACTION_MAIN` + `CATEGORY_LAUNCHER`).
   - Uses `UsageStatsManager` (`PACKAGE_USAGE_STATS`), Overlay (`SYSTEM_ALERT_WINDOW`), and a Foreground Service (`FOREGROUND_SERVICE_SPECIAL_USE`) to present a PIN gate (`AppLockGateActivity`) when a locked app opens.

5. **Security & Settings**
   - `FLAG_SECURE` active inside the vault to block screenshots and Recents previews.
   - Automatic vault lock when backgrounded (`onStop`).
   - Passphrase-encrypted `.hcxbackup` export and restore via Storage Access Framework.
   - Selectable dark neon themes (`Obsidian Neon`, `Cyber Violet`, `Stealth Minimal`).

## Building an APK

### Option 1: Export from AI Studio
Use the AI Studio **Settings / Export** menu to download the project as a **ZIP** or generate an **APK / AAB** directly.

### Option 2: Build Locally in Android Studio / Command Line
1. Extract the ZIP and open the project root in **Android Studio** (or ensure Gradle + Android SDK 36 are installed).
2. Build a debug APK:
   ```bash
   gradle :app:assembleDebug
   ```
   The output APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
3. Run unit & Robolectric tests:
   ```bash
   gradle :app:testDebugUnitTest
   ```
