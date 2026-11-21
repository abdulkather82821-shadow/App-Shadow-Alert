# Shadow Alert

**"Driven by Intent. Shaped by Excellence."**  
*Plan before use. Stay aware during use. Take control after use.*

Shadow Alert is a native Android digital well-being application built in **Kotlin** and **Jetpack Compose** based on the research manuscript:  
*"Design and Development of Shadow Alert: An Intent-Based Mobile System for Real-Time Screen Monitoring and Usage Indications."*

---

## Architecture & Tech Stack

- **Platform:** Native Android (`minSdk = 26` / Android 8.0, `targetSdk = 35` / Android 15)
- **Language:** Kotlin 2.0.21
- **UI & Design System:** Jetpack Compose + Material Design 3 in a Navy-Blue & Cyan palette (Light & Dark themes, custom Canvas charts, adaptive vector shield+clock icon)
- **Architecture:** MVVM with manual dependency container (`AppContainer`) and reactive `StateFlow`/`SharedFlow`
- **Local Persistence:**
  - **Room Database (v2 with `MIGRATION_1_2`)**: `AppLimitEntity`, `IntentSessionEntity`, `AlertEventEntity`, `ReflectionEntity`, `DailyAppUsageEntity`
  - **Jetpack DataStore Preferences**: onboarding state, theme mode, intervention level, warning thresholds, daily target, reset hour, active session recovery ID
- **System Integration:**
  - `UsageStatsManager` (`UsageEvents` chronological foreground/background + `SCREEN_NON_INTERACTIVE`/`KEYGUARD_SHOWN` state machine, plus `INTERVAL_DAILY` fallback for older days)
  - `PackageManager` (`ACTION_MAIN` + `CATEGORY_LAUNCHER` via `<queries>`) for launchable app discovery & category inference
  - `LifecycleService` foreground service (`FOREGROUND_SERVICE_SPECIAL_USE` on Android 14+) that suspends polling completely while the screen is off/locked (`ACTION_SCREEN_OFF` / `ACTION_SCREEN_ON`)
  - `NotificationManager` with dedicated channels (`Monitoring`, `Warnings`, `Limit & Intervention Prompts`)
  - `BootReceiver` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`) and `PackageChangeReceiver` (`PACKAGE_FULLY_REMOVED`)

---

## 4-Level Intervention Architecture

1. **Level 1 (5 minutes remaining):** Gentle supportive notification reminding the user of remaining planned time.
2. **Level 2 (2 minutes remaining):** Stronger supportive notification prompting preparation to wrap up or reflect.
3. **Level 3 (Intended duration or Daily limit reached):** Configurable intervention based on user preference (`Gentle Notifications`, `Pause & Reflection Prompt`, or `Strong Overlay Shield` when `SYSTEM_ALERT_WINDOW` is granted).
4. **Level 4 (Intentional Override):** Requires deliberate confirmation (explicit acknowledgement checkbox + optional reason) and logs every override locally alongside temporary extensions (`+5m`/`+10m`/`+15m`), `Take a Break`, `Return to Device Home Screen`, and post-session reflections.

---

## Privacy & Honest Android Platform Limitations

- **100% Offline & Local:** Shadow Alert does **not** declare `android.permission.INTERNET`. No accounts, telemetry, cloud sync, message reading, browsing history, or contacts access exist.
- **No Accessibility Service Abuse:** Google Play policies restrict `AccessibilityService` strictly to disability/assistive tools and prohibit its use for screen-time app blocking or terminating apps. Ordinary Android apps cannot forcibly kill other applications; Shadow Alert respects Android security boundaries and user autonomy.
- **Overlay & Background Launch Compliance (Android 10+):** Android blocks background services from directly launching Activities without `SYSTEM_ALERT_WINDOW`. When Overlay permission is not granted, Shadow Alert reliably falls back to a high-priority Pause & Reflection notification (`PendingIntent` / `fullScreenIntent`).
- **Usage Access Fallback:** When `PACKAGE_USAGE_STATS` is denied or revoked, Shadow Alert never simulates usage—it marks automatic monitoring as paused and provides an explicit **Manual Session Timer** fallback.

---

## Direct APK Download (Android 8.0+)

Download the latest directly installable APK from GitHub Releases:

- **Direct APK Link:** [Shadow-Alert.apk](https://github.com/abdulkather82821-shadow/App-Shadow-Alert/releases/latest/download/Shadow-Alert.apk)
- **Releases Page:** [GitHub Releases](https://github.com/abdulkather82821-shadow/App-Shadow-Alert/releases)

*(On your Android phone, open the link above, allow **Install unknown apps** for your browser if prompted, and install `Shadow-Alert.apk`.)*

---

## Setup & APK Build Instructions

1. Install **Android Studio Ladybug (2024.2)** or newer with **JDK 17** and **Android SDK Platform 35**.
2. Open the repository root folder (`/App-Shadow-Alert`) in Android Studio and let Gradle sync (`AGP 8.7.3`, `Kotlin 2.0.21`, `Gradle 8.14.8`).
3. Run unit tests, Lint, and build the debug APK:
   ```bash
   gradle testDebugUnitTest lintDebug assembleDebug
   ```
   *(Or via Android Studio's Gradle wrapper once generated in IDE: `./gradlew testDebugUnitTest lintDebug assembleDebug`)*
4. Debug APK output location:
   `app/build/outputs/apk/debug/app-debug.apk`

### Signed Release APK Build Instructions

1. Generate a release keystore (keep outside Git; `.gitignore` excludes `*.keystore` and `*.jks`):
   ```bash
   keytool -genkey -v -keystore shadow-alert-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias shadowalert
   ```
2. Export signing environment variables:
   ```bash
   export SHADOW_ALERT_KEYSTORE_PATH="/path/to/shadow-alert-release.jks"
   export SHADOW_ALERT_KEYSTORE_PASSWORD="your-store-password"
   export SHADOW_ALERT_KEY_ALIAS="shadowalert"
   export SHADOW_ALERT_KEY_PASSWORD="your-key-password"
   ```
3. Build and verify:
   ```bash
   gradle assembleRelease
   apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
   ```

---

## Testing

See [`TESTING.md`](TESTING.md) for the unit test suite and 15-point physical-device verification plan.
