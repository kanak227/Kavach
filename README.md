# Kavach — Smart Anti-Theft & Stealth Motion Guard

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg?logo=android)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-brightgreen.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-35-blue.svg)](https://developer.android.com)
[![Author](https://img.shields.io/badge/Author-Kanak%20Verma-orange.svg)](https://github.com/kanak227)
[![GitHub](https://img.shields.io/badge/GitHub-kanak227-181717.svg?logo=github)](https://github.com/kanak227)
[![Repository](https://img.shields.io/badge/Repo-kanak227%2Fkavach-blue.svg?logo=github)](https://github.com/kanak227/kavach)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)](LICENSE)

A next-generation Android security utility that outsmarts phone thieves using human psychology, dual-sensor motion detection, and tamper-resistant protocols — protecting devices while charging, resting on tables, or overnight.

---

## 🎯 The Problem with Traditional Anti-Theft Apps
Most anti-theft apps fail because of **The Panic Trap**:
1. When a thief picks up a phone, traditional apps immediately scream and flash a giant red warning: *"ALARM IN 5 SECONDS"*.
2. **What does the thief do?** They instantly smash the volume buttons, force-restart, or power down before the owner is notified.
3. Furthermore, generic motion sensors cause constant **false alarms** inside pockets and bags.

---

## 🛡️ The Kavach Solution

### 1. 🕰️ Stealth Decoy Trap
When motion is detected, Kavach **does not panic or flash red**. Instead, it displays an innocent ambient clock screen.
- The thief assumes the device is in normal lock-screen standby and feels no urgency to hide or tamper with the device.
- The real owner has a configurable **grace countdown** to authenticate via Biometrics (Fingerprint, Face Unlock, or PIN).

### 2. 🔒 Single Notification Architecture
Runs completely silently in the background with zero status-bar clutter. Only a single high-priority alert appears upon motion trigger:
- **"Kavach Security Alert — Tap to authenticate"**
- Tapping the banner from the lock screen immediately opens the secure verification prompt to disarm in 0ms without unlocking the device first.

### 3. 🔊 Autonomous Tamper Resistance & Volume Floor Lock
If an intruder attempts to mute the device during an active alarm, Kavach dynamically forces the audio stream to maximum volume in real time. If the phone is rebooted while armed, `BootReceiver` automatically recovers the security state immediately upon system restart.

### 4. 📳 Dual-Sensor Pocket Fusion (Zero False Alarms)
Combines Accelerometer, Ambient Light Sensor, and Proximity Sensor readings. When Pocket Guard is active, walking motions and leg swings are filtered out; only extracting the phone from a pocket or bag triggers the alarm.

### 5. 🚨 Detonation Alarm & Flashlight Strobe
If the countdown expires without authorized biometric or PIN verification:
- Blasts high-decibel siren at 100% volume (overriding silent and vibrate modes).
- Rapid tactical camera LED strobe flasher.
- Continuous haptic vibration.
- Emergency breach screen with one-touch authenticate-to-silence for the owner.

### 6. 🔊 Custom Siren Sound Packs
Includes built-in synthesized 44.1kHz audio packs:
- 🚨 **Police Siren**: High-frequency wailing klaxon
- 📢 **Air Horn**: Triple-tone stadium air horn blast
- 📡 **Military Radar**: Tactical red alert chirp sweep
- ☢️ **Nuclear Alert**: Deep resonant emergency broadcast horn
- 🌪️ **Tornado Warning**: Continuous dual-phase emergency siren

### 7. 📖 Built-In Comprehensive User Manual
Includes a complete, searchable in-app user guide translated across 12 languages with step-by-step instructions for:
- Desk Guard vs Pocket Guard
- Sensitivity tuning & siren sound testing
- Lock screen authentication & troubleshooting

---

## 🏛️ Architecture & Tech Stack

```
com.kavach.app/
├── MainActivity.kt                      -> Single Activity architecture & navigation lifecycle
├── alarm/
│   └── AlarmController.kt               -> Max volume audio, strobe torch, and haptics
├── auth/
│   └── BiometricAuthManager.kt          -> Android BiometricPrompt framework (Fingerprint/Face/PIN)
├── data/
│   ├── Models.kt                        -> ShieldState, TimerPreset, SirenPack, SensorReading
│   └── KavachPreferences.kt             -> Jetpack DataStore persistent settings
├── sensor/
│   └── SensorFusion.kt                  -> Accelerometer + Light + Proximity sensor fusion
├── service/
│   ├── KavachForegroundService.kt       -> Background guard service with silent monitoring
│   └── BootReceiver.kt                  -> Restores armed state after device reboot
├── viewmodel/
│   └── DashboardViewModel.kt            -> State machine (DISARMED -> ARMING -> ARMED -> TRIGGERED -> ALARM)
└── ui/
    ├── theme/                           -> Cyberpunk neon design system (Orbitron & Rajdhani fonts)
    ├── components/                      -> Animated Radar Ring, Timer Row, Sensor Status Bar
    └── screens/                         -> Dashboard, Decoy Clock, Alarm Active, Settings, Siren Packs, User Manual
```

- **Language:** Kotlin 2.2.10
- **UI Framework:** Jetpack Compose with Material 3
- **Architecture:** MVVM + Clean Architecture + StateFlow
- **Persistence:** Jetpack Preferences DataStore
- **Sensors:** Android Hardware SensorManager (Sensor Fusion)
- **Security:** AndroidX Biometric API
- **Audio:** Custom PCM WAV Sound Generators
- **Target Platform:** Android 8.0 (API 26) through Android 15 (API 35)

---

## 🛠️ Build & Installation

### Prerequisites
- Android Studio Ladybug / Meerkat (or later)
- JDK 17 or later
- Android SDK 35+ / API 26+

### 📥 Clone & Build
```bash
# Clone the repository
git clone https://github.com/kanak227/kavach.git

# Navigate into the project directory
cd kavach

# Build Debug APK
# Linux / macOS:
./gradlew assembleDebug

# Windows PowerShell:
.\gradlew.bat assembleDebug
```

The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📱 Testing on Device
1. Install the APK on a physical Android phone:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
2. Grant requested permissions:
   - Notifications (required for emergency authentication alert)
   - Biometric / Lockscreen credential (for disarming security shield)
3. Tap **ARM SHIELD**, place the phone flat on a desk, and pick it up to test the stealth decoy trap!

---

## 🔒 Privacy & Data Protection
Kavach is built around total offline privacy:
- **100% Offline:** The app does not request or use internet permissions.
- **Zero Data Collection:** No personal, telemetry, sensor, or biometric data is ever collected, stored remotely, or shared.
- **Official Policy:** Read our public [Privacy Policy](https://github.com/kanak227/kavach/blob/main/privacy-policy.html).

---

## 📄 License
This project is licensed under a **Proprietary Source Code & Distribution License**. See [`LICENSE`](LICENSE) for details.

> **CRITICAL NOTICE:** Unauthorized publishing, redistribution, or deployment of this application (or its derivatives) on the Google Play Store, Samsung Galaxy Store, or any other app marketplace is **strictly prohibited** without express written permission from **Kanak Verma**.
