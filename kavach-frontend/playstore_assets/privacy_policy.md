# Privacy Policy for Kavach

**Effective Date:** October 24, 2026  
**Application:** Kavach: Smart Anti-Theft & Stealth Motion Guard  

At Kavach, we are completely committed to protecting your privacy and security. Kavach is built from the ground up as a **100% offline, zero-data-collection security utility**. This Privacy Policy explains how our application operates, what permissions it requests, and why your data never leaves your device.

---

### 1. Zero Data Collection Principle
- **No Personal Data Collected**: Kavach does not collect names, email addresses, phone numbers, contacts, accounts, or passwords.
- **No Location Tracking**: Kavach does not request, track, collect, or store your GPS, Wi-Fi, or network location.
- **No Camera or Microphone Access**: Kavach never takes photos, records video, or records audio. The camera lens and microphone are never accessed. The flashlight torch uses hardware toggle capabilities strictly without camera sensor access.
- **No Biometric Data Stored**: Biometric verification (fingerprint and face authentication) is handled entirely by Android’s native, secure system `BiometricPrompt` and hardware secure enclave. Kavach never sees, processes, or stores your biometric templates.
- **No Internet or Network Traffic**: Kavach requests **zero network or internet permissions**. The app operates entirely locally on your device hardware with 0 bytes transmitted externally.
- **No Advertisements or Third-Party SDKs**: Kavach does not contain advertising SDKs, analytics trackers, or user profiling tools.

---

### 2. Device Permissions and Why We Need Them

To perform its anti-theft and motion monitoring functions, Kavach requests strictly the minimum necessary permissions:

| Permission / Hardware | Purpose & Scope |
|---|---|
| **Device Sensors (Accelerometer, Light, Proximity)** | Used in real time to detect physical device displacement, bumps, or pocket removal while armed. All sensor data is processed in volatile memory and immediately discarded. Never logged or saved. |
| **Flashlight (`FLASHLIGHT`)** | Used solely to toggle the LED flashlight torch for visual distress signaling during an intrusion alarm. Camera permission is NOT requested. *(Note: Flashlight strobe can be disabled in Settings for photosensitive comfort).* |
| **Vibrate (`VIBRATE`)** | Emits gentle tactile confirmation pulses to the owner during the stealth decoy phase, and high-intensity vibration during a full alarm. |
| **Foreground Service (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`)** | Allows Kavach to keep motion detection active and maintain security while the screen is off or resting on a table/desk. A persistent notification is displayed while armed. |
| **Full Screen Intent (`USE_FULL_SCREEN_INTENT`)** | Allows Kavach to display the emergency verification and disarm interface over the lock screen immediately when unauthorized motion is detected. |
| **Boot Completed (`RECEIVE_BOOT_COMPLETED`)** | Automatically restores your armed security shield if the phone is rebooted while armed. |
| **Biometric (`USE_BIOMETRIC`)** | Allows the owner to quickly and securely disarm the alarm using Android's native fingerprint, face, or device PIN authentication. |
| **Audio Volume Management** | During an active emergency alarm, Kavach ensures the alarm siren is audible at the user's selected volume floor to prevent an unauthorized intruder from silencing the alarm. |

---

### 3. Data Safety & Local Storage
- **Local Settings Storage**: Your preferences (such as selected siren pack, detection sensitivity, and night schedule hours) are stored strictly on your local device storage using Android Jetpack DataStore.
- **Zero Third-Party Sharing**: Because Kavach has no internet connection, no data is ever shared with third parties or cloud servers.
- **No Tracking**: Kavach does not use Google Advertising ID (GAID) or device identifiers.

---

### 4. Children's Privacy
Kavach does not collect personal information from any user, including children under the age of 13.

---

### 5. Policy Updates & Contact
If you have any questions or suggestions regarding this Privacy Policy, please contact the developer via the support contact email on our Google Play Store listing.
