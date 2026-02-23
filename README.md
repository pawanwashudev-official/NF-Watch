# ⌚ NF Watch — Premium Universal Companion for Da Fit Protocol

NF Watch is a state-of-the-art, open-source Android companion application designed specifically for devices using the **Moyoung (Da Fit) protocol**. This includes popular brands like **GoBoult**, **Drift**, **Colmi**, **Fire-Boltt**, and many other Da Fit-skinned wearables.

It prioritizes **Zero-Touch Connectivity**, **Extreme Anti-Theft Security**, and **100% Offline Privacy**.

## 🎯 Compatibility
NF Watch is compatible with any smartwatch that traditionally connects via the **Da Fit** app or its localized variants. By using the raw Moyoung protocol, it provides a faster, more reliable, and private alternative to standard companion apps.

## 🚀 Key Features

### 📡 Zero-Touch Background Connectivity
*   **Gatt Auto-Connect**: Uses specialized Bluetooth hardware hooks to reconnect instantly when you walk into range, without waking the phone's CPU.
*   **Smart Reconnection**: Unlike standard apps, NF Watch handles Bluetooth toggles gracefully. It intercepts system "Bluetooth ON" signals to force a fresh connection burst, restoring your link in seconds without needing to open the app.
*   **The Hour Waker**: A WorkManager safety net that runs every hour to guarantee background sync even if the system kills the app.

### 🚨 Aggressive Anti-Theft "Find My Phone" (SOS Mode)
*   **SOS Piercing Mode**: Synthesizes a **3.2kHz high-frequency sine wave** (safe for speakers) that is audible from extreme distances.
*   **Volume Guardian**: Automatically resets volume to **100% Every 3 Seconds** during an alert. It’s impossible to silence.
*   **Synced SOS Pattern**: The flashlight, vibration, and sound pulse in a synchronized rhythmic SOS pattern (`... --- ...`).
*   **Watch-Only Termination**: The alert can **ONLY** be stopped from the watch; the phone becomes a dedicated emergency beacon.

### 🏥 Health & Privacy
*   **Health Connect Integration**: Two-way synchronization with Android's native health storage.
*   **Real-time Vitals**: Live tracking for Steps, Calories, Heart Rate, SpO2, and Blood Pressure.
*   **100% Offline**: All data stays on your device. No cloud accounts, no data selling, no tracking.
*   **Premium Glassmorphism UI**: A dark-themed, immersive interface with buttery smooth micro-animations.

## 🛠️ Technical Stack
*   **Language**: 100% Kotlin
*   **UI Framework**: Jetpack Compose (Material 3)
*   **Local Storage**: Room DB & DataStore Preferences
*   **Protocol**: Native implementation of the Moyoung (Da Fit) BLE Protocol.
*   **Optimization**: R8 enabled with custom ProGuard rules for minimal binary size and code protection.

## 🏗️ Build Instructions
1.  Ensure you have the latest **Android SDK** (API 35).
2.  Add your signing keys to `local.properties`.
3.  Run `.\gradlew installrelease` to build the optimized production APK.

---
*Developed for power users who value security, speed, and privacy.*
