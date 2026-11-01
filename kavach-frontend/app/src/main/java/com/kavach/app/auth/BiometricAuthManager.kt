package com.kavach.app.auth

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Universal authentication manager supporting:
 * 1. Fingerprint (Class 3 / BIOMETRIC_STRONG)
 * 2. Face Unlock (Class 2 / BIOMETRIC_WEAK on OnePlus, Oppo, Realme, Samsung, Pixel)
 * 3. Device Credential fallback (PIN / Pattern / Password via KeyguardManager)
 */
class BiometricAuthManager(private val activity: FragmentActivity) {

    companion object {
        const val REQUEST_CODE_DEVICE_CREDENTIAL = 8888
    }

    private var biometricPrompt: BiometricPrompt? = null
    private var pendingSuccessCallback: (() -> Unit)? = null
    private var isAuthInProgress = false

    /**
     * Check if device has any biometric hardware enrolled or device credential set.
     */
    fun isAnyAuthAvailable(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        val canBiometric = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        if (canBiometric == BiometricManager.BIOMETRIC_SUCCESS) return true

        val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager?.isDeviceSecure == true
    }

    /**
     * Show biometric authentication prompt with Face Unlock, Fingerprint,
     * and "Use PIN / Password" fallback button.
     */
    fun authenticate(
        onSuccess: () -> Unit,
        onFailure: () -> Unit,
        onError: (Int, String) -> Unit
    ) {
        if (isAuthInProgress) return
        isAuthInProgress = true
        pendingSuccessCallback = onSuccess

        val biometricManager = BiometricManager.from(activity)
        val canBiometric = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        )

        if (canBiometric == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(activity)

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isAuthInProgress = false
                    pendingSuccessCallback = null
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailure()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    isAuthInProgress = false
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_LOCKOUT ||
                        errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT) {
                        // User tapped "Use PIN / Password" or biometric locked out — launch Keyguard device credential
                        if (!launchDeviceCredential()) {
                            onError(errorCode, errString.toString())
                        }
                    } else {
                        onError(errorCode, errString.toString())
                    }
                }
            }

            biometricPrompt = BiometricPrompt(activity, executor, callback)

            // CRITICAL: We combine BIOMETRIC_STRONG and BIOMETRIC_WEAK so OnePlus/Oppo Face Unlock
            // and Fingerprint work simultaneously. We provide setNegativeButtonText for PIN/Password
            // to strictly avoid the Android IllegalArgumentException caused by combining DEVICE_CREDENTIAL with WEAK!
            // setConfirmationRequired(false) ensures face unlock immediately succeeds without an extra touch.
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Kavach Shield Disarm")
                .setSubtitle("Use Fingerprint, Face Unlock, or PIN")
                .setDescription("Glance at camera, touch sensor, or tap below")
                .setConfirmationRequired(false)
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
                .setNegativeButtonText("Face Unlock / PIN / Password")
                .build()

            try {
                biometricPrompt?.authenticate(promptInfo)
            } catch (e: Exception) {
                // In case of any OEM compatibility issue, fallback to system PIN/Password/Face immediately
                if (!launchDeviceCredential()) {
                    isAuthInProgress = false
                    pendingSuccessCallback = null
                    onSuccess()
                }
            }
        } else {
            // No biometric enrolled — directly launch system PIN / Pattern / Password / Face
            if (!launchDeviceCredential()) {
                // No lock screen security configured on device — disarm directly
                isAuthInProgress = false
                pendingSuccessCallback = null
                onSuccess()
            }
        }
    }

    /**
     * Directly launch the system Keyguard screen (where OEM Face Unlock, PIN, Pattern,
     * and Password are fully supported by OnePlus, Samsung, Xiaomi, Pixel, etc.).
     */
    fun launchFaceOrCredentialAuth(onSuccess: () -> Unit): Boolean {
        isAuthInProgress = true
        pendingSuccessCallback = onSuccess
        val launched = launchDeviceCredential()
        if (!launched) {
            isAuthInProgress = false
            pendingSuccessCallback = null
        }
        return launched
    }

    /**
     * Launch the native system PIN, Pattern, Password, or Face screen via KeyguardManager.
     */
    fun launchDeviceCredential(): Boolean {
        val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguardManager != null && keyguardManager.isDeviceSecure) {
            val confirmIntent = keyguardManager.createConfirmDeviceCredentialIntent(
                "Kavach Shield Disarm",
                "Enter PIN, Pattern, or Password"
            )
            if (confirmIntent != null) {
                try {
                    @Suppress("DEPRECATION")
                    activity.startActivityForResult(confirmIntent, REQUEST_CODE_DEVICE_CREDENTIAL)
                    return true
                } catch (_: Exception) { }
            }
        }
        return false
    }

    /**
     * Called when KeyguardManager credential activity returns Activity.RESULT_OK
     */
    fun onCredentialSuccess() {
        isAuthInProgress = false
        pendingSuccessCallback?.invoke()
        pendingSuccessCallback = null
    }

    /**
     * Cancel any ongoing authentication prompt.
     */
    fun cancelAuthentication() {
        isAuthInProgress = false
        biometricPrompt?.cancelAuthentication()
        pendingSuccessCallback = null
    }
}
