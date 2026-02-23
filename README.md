# ⌚ NF Watch — Premium Android Companion

NF Watch is a state-of-the-art Android companion application designed for GoBoult and Drift series smartwatches. It prioritizes **Zero-Touch Connectivity**, **Extreme Security**, and **Offline Privacy**.

## 🚀 Key Features

### 📡 Zero-Touch Background Connectivity
*   **Gatt Auto-Connect**: Uses specialized Bluetooth hardware hooks to reconnect instantly when you walk into range, without waking the phone's CPU.
*   **Toggle-Decouling**: The app maintains its connection state even if you toggle system Bluetooth OFF/ON, ensuring no manual intervention is needed.
*   **High-Priority Reconnection**: A 15-second active burst mode triggers upon Bluetooth activation to ensure an immediate link.
*   **The Hour Waker**: A WorkManager safety net that runs every hour to guarantee background sync even if the system kills the app.

### 🚨 Aggressive Anti-Theft "Find My Phone"
*   **SOS Piercing Mode**: Synthesizes a **3.2kHz high-frequency sine wave** (safe for speakers) that is audible from extreme distances.
*   **Volume Guardian**: Automatically resets volume to **100% Every 3 Seconds** during an alert, making it impossible to silence.
*   **Synced SOS Pattern**: The flashlight, vibration, and sound pulse in a synchronized rhythmic SOS pattern (`... --- ...`).
*   **Theft Persistence**: The alert continues even if the watch-phone link is severed, screaming until found.
*   **Watch-Only Termination**: The alert can **ONLY** be stopped from the watch; no phone-side "STOP" button exists.

### 🏥 Health & Privacy
*   **Health Connect Integration**: Two-way synchronization with Android's native health storage.
*   **Real-time Vitals**: Live tracking for Steps, Calories, Heart Rate, SpO2, and Blood Pressure.
*   **100% Offline**: All data stays on your device. No cloud accounts, no data selling, no tracking.
*   **Premium Glassmorphism UI**: A dark-themed, immersive interface with buttery smooth micro-animations.

## 🛠️ Technical Stack
*   **Language**: 100% Kotlin
*   **UI Framework**: Jetpack Compose (Material 3)
*   **Local Storage**: Room DB & DataStore Preferences
*   **Background Processing**: WorkManager & Foreground Services
*   **Optimization**: R8 enabled with custom ProGuard rules for maximum security and minimal binary size.

## 🏗️ Build Instructions
1.  Ensure you have the latest **Android SDK** (API 35).
2.  Add your signing keys to `local.properties`.
3.  Run `.\gradlew installrelease` to build the optimized production APK.

---
*Developed with focus on reliability and security.*
