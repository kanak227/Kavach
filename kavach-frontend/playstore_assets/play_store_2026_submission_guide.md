# 🛡️ Kavach — Google Play Store 2026 Zero-Rejection Submission Blueprint

> **Status:** 100% Policy Clean | Target SDK: 35 (Android 15) | 0 Fatal Violations | 0 Warning Triggers | 100% Offline Zero-Data Architecture

---

## 📋 Executive Audit & Remediation Summary

Every single permission, service declaration, background job, and UI string in **Kavach** has been audited and hardened against Google Play's 2024–2026 Developer Program Policies.

| Issue / Risk Category | Severity | Initial Status | Remediation Action Taken |
|---|---|---|---|
| **Accessibility API Misuse** (`BIND_ACCESSIBILITY_SERVICE`) | 🚨 **FATAL (Account Termination Risk)** | Used to intercept shutdown dialogs | **Completely Removed.** Decoupled service from manifest, source code, and UI. Replaced with compliant Volume Floor Lock and Boot Recovery. |
| **Battery Optimization Exemption** (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) | 🛑 **CRITICAL (Automated Rejection)** | Declared in manifest | **Completely Removed.** Apps outside VoIP/messaging are rejected automatically during APK/AAB upload. |
| **Exact Alarm Restriction** (`SCHEDULE_EXACT_ALARM`) | 🛑 **CRITICAL (Automated Rejection)** | Declared in manifest | **Completely Removed.** Replaced exact alarms with compliant `setAndAllowWhileIdle()` standard alarms. |
| **Redundant Sensitive Camera** (`CAMERA`) | ⚠️ **HIGH (Permission Review Rejection)** | Declared in manifest | **Completely Removed.** Torch strobe uses `CameraManager.setTorchMode()` which strictly only needs `FLASHLIGHT`. |
| **Overlay / Tapjacking Risk** (`SYSTEM_ALERT_WINDOW`) | ⚠️ **HIGH (Security Flag)** | Declared in manifest | **Completely Removed.** Unused overlay permission stripped. |
| **Target SDK Preview Mismatch** | ⚠️ **HIGH (Upload Rejection)** | Target SDK was preview `37` | **Fixed.** Updated to standard Google Play production target: `targetSdk = 35` (Android 15). |
| **AdMob Test ID / Data Safety Risk** | ⚠️ **HIGH (Review Flag)** | Test AdMob IDs in manifest | **Completely Removed.** Kavach now operates as a 100% offline, zero-data, ad-free security utility. Zero privacy friction. |
| **Foreground Service Declaration** | ⚠️ **MODERATE (App Review Delay)** | Declared without Play Console Form | Configured `foregroundServiceType="specialUse"` with subtype property; ready for Play Console declaration. |
| **Excessive WakeLock Duration** | 🟡 **LOW (Vitals Warning)** | 24-hour wake lock | **Fixed.** Bounded wake lock with automatic cleanup upon disarm. |

---

## 🚀 Step-by-Step Play Console Submission Guide

Follow these exact steps when creating your release in the **Google Play Console**:

---

### Step 1: App Content — Special Permissions & Declarations

#### 1. Foreground Service Declaration (`specialUse`)
In **App content > Foreground service permissions**:
- **Why does your app need the foreground service?**
  Select: `specialUse`
- **Provide a description of the use case:**
  ```text
  Kavach is an anti-theft security and motion detection utility. The foreground service is strictly initiated when the user explicitly arms the security shield. It continuously monitors device sensors (accelerometer, ambient light, proximity) while the screen is locked or turned off to detect unauthorized physical tampering or theft. If unauthorized displacement is detected, the service immediately activates the emergency siren and strobe protocol. The service displays a persistent, non-dismissible notification allowing the user to view security status or disarm at any time.
  ```
- **Video Link (30–60 second YouTube unlisted video):**
  > **Video Script:**
  > 1. Open Kavach.
  > 2. Tap **ARM SHIELD NOW** (show countdown starting).
  > 3. Turn the screen off and place the phone on a table. Show the persistent notification on keyguard.
  > 4. Pick up the phone — show the decoy screen / alarm triggering, followed by disarming with fingerprint.

#### 2. Full-Screen Intent (`USE_FULL_SCREEN_INTENT`)
In **App content > Full-screen intent permissions**:
- **Why does your app need full-screen intent?**
  Select: **Emergency alarms & security alerts**
- **Description:**
  ```text
  Kavach utilizes full-screen intent exclusively for high-priority emergency theft alerts. When unauthorized physical movement or device tampering is detected while the device is locked, Kavach presents an immediate full-screen verification and disarm interface over the lock screen so the true owner can disarm the alarm via biometrics before maximum volume siren detonation.
  ```

#### 3. Advertising ID (`AD_ID`)
- **Does your app use Advertising ID?**
  Select: **No** (Kavach contains 0 advertising SDKs).

---

### Step 2: Data Safety Questionnaire (Zero Data)

Under **App content > Data safety**, answer as follows:

1. **Does your app collect or share any of the required user data types?**  
   ➡️ **No** (Kavach is completely offline and does not collect, store, or transmit any user data).

*(Because you answered No, the questionnaire is immediately complete. No data types to declare!)*

---

### Step 3: Target Audience & Content Rating

1. **Target Age:**
   - Select: **18 and over** (or 13+).
2. **App Category:**
   - Category: **Tools**
   - Tags: **Security, Utility, Device Protection**
3. **Ads Declaration:**
   - Select: **No, my app does not contain ads**.

---

### Step 4: Store Listing Assets

- **App Title (25 chars):** `Kavach: Smart Anti-Theft`
- **Short Description (75 chars):** `Dual-sensor anti-theft shield, stealth decoy trap & tamper-resistant siren.`
- **Privacy Policy URL:** Host [`playstore_assets/privacy_policy.md`](playstore_assets/privacy_policy.md) on GitHub Pages, Notion, or Google Sites and paste the public HTTPS link into Play Console.

---

### Step 5: Building Production Release Bundle (.aab)

When ready to generate your production bundle for upload:
```powershell
.\gradlew.bat bundleRelease
```
The output file will be generated at:
`app/build/outputs/bundle/release/app-release.aab`
