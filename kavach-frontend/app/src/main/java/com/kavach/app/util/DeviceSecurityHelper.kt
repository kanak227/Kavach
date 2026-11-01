package com.kavach.app.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

data class BrandSecurityInfo(
    val brandDisplayName: String,
    val isNativeShutdownLockSupported: Boolean,
    val featureName: String,
    val locationSteps: List<String>,
    val explanation: String
)

object DeviceSecurityHelper {

    fun getDeviceBrandSecurityInfo(): BrandSecurityInfo {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()

        return when {
            manufacturer.contains("samsung") || brand.contains("samsung") -> {
                BrandSecurityInfo(
                    brandDisplayName = "Samsung Galaxy",
                    isNativeShutdownLockSupported = true,
                    featureName = "Lock network and security",
                    locationSteps = listOf(
                        "Open Device Settings below",
                        "Tap 'Lock screen and AOD' (or 'Lock screen')",
                        "Tap 'Secure lock settings' (Enter your PIN/Pattern)",
                        "Turn ON 'Lock network and security'"
                    ),
                    explanation = "When enabled, your phone will require your fingerprint or PIN before allowing anyone to turn off or restart your phone from the lock screen."
                )
            }
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
            manufacturer.contains("redmi") || brand.contains("redmi") ||
            manufacturer.contains("poco") || brand.contains("poco") -> {
                BrandSecurityInfo(
                    brandDisplayName = "Xiaomi / Redmi / POCO",
                    isNativeShutdownLockSupported = true,
                    featureName = "Require password to power off",
                    locationSteps = listOf(
                        "Open Device Settings below",
                        "Tap 'Passwords & security' (or 'Security')",
                        "Look for 'Privacy protection' or 'Advanced lock settings'",
                        "Turn ON 'Require password to power off'"
                    ),
                    explanation = "Prevents thieves from accessing the shutdown slider on the lock screen without entering your security password."
                )
            }
            manufacturer.contains("oppo") || brand.contains("oppo") ||
            manufacturer.contains("oneplus") || brand.contains("oneplus") ||
            manufacturer.contains("realme") || brand.contains("realme") -> {
                BrandSecurityInfo(
                    brandDisplayName = "OPPO / OnePlus / Realme",
                    isNativeShutdownLockSupported = true,
                    featureName = "Require password to power off",
                    locationSteps = listOf(
                        "Open Device Settings below",
                        "Tap 'Security & Privacy' (or 'System Security')",
                        "Tap 'More Security Settings'",
                        "Turn ON 'Require password to power off'"
                    ),
                    explanation = "ColorOS / OxygenOS will completely block the shutdown slider on your lock screen unless your PIN or fingerprint is verified."
                )
            }
            manufacturer.contains("vivo") || brand.contains("vivo") ||
            manufacturer.contains("iqoo") || brand.contains("iqoo") -> {
                BrandSecurityInfo(
                    brandDisplayName = "Vivo / iQOO",
                    isNativeShutdownLockSupported = true,
                    featureName = "Screen lock privacy",
                    locationSteps = listOf(
                        "Open Device Settings below",
                        "Tap 'Fingerprint, face and password' (or 'Privacy & Security')",
                        "Look for 'Screen lock privacy' or 'Require password to power off'",
                        "Toggle the protection ON"
                    ),
                    explanation = "Locks the power menu when your phone is armed, preventing unauthorized restart or power-off."
                )
            }
            else -> {
                val displayName = when {
                    manufacturer.contains("google") || brand.contains("google") -> "Google Pixel"
                    manufacturer.contains("motorola") || brand.contains("motorola") -> "Motorola"
                    manufacturer.contains("nothing") || brand.contains("nothing") -> "Nothing Phone"
                    manufacturer.contains("sony") || brand.contains("sony") -> "Sony Xperia"
                    else -> Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
                }
                BrandSecurityInfo(
                    brandDisplayName = displayName,
                    isNativeShutdownLockSupported = false,
                    featureName = "Kavach Instant Alarm Defense",
                    locationSteps = listOf(
                        "Keep Kavach Pocket Guard armed during travel",
                        "Ensure 'Volume Floor Lock' is active in Kavach",
                        "Keep 'Hold Power for Assistant' ON in system gestures"
                    ),
                    explanation = "Stock Android does not yet offer a native PIN-to-power-off toggle. Kavach protects your device with instant 100% volume alarms and volume down lock the moment extraction is detected, catching pickpockets in seconds."
                )
            }
        }
    }

    fun getAllBrandCorrelationList(): List<BrandSecurityInfo> {
        return listOf(
            BrandSecurityInfo(
                brandDisplayName = "Samsung (One UI 2.0+)",
                isNativeShutdownLockSupported = true,
                featureName = "Lock network and security",
                locationSteps = listOf("Settings > Lock screen > Secure lock settings > Lock network & security"),
                explanation = "Forces PIN/Fingerprint before power-off or network toggle from lock screen."
            ),
            BrandSecurityInfo(
                brandDisplayName = "Xiaomi / Redmi / POCO (MIUI 14 / HyperOS)",
                isNativeShutdownLockSupported = true,
                featureName = "Require password to power off",
                locationSteps = listOf("Settings > Passwords & security > Privacy > Require password to power off"),
                explanation = "Locks the shutdown slider completely behind your lock screen password."
            ),
            BrandSecurityInfo(
                brandDisplayName = "OPPO / OnePlus / Realme (ColorOS / OxygenOS 13+)",
                isNativeShutdownLockSupported = true,
                featureName = "Require password to power off",
                locationSteps = listOf("Settings > Security & Privacy > More security > Require password to power off"),
                explanation = "Stops unauthorized lock screen shutdowns without biometric/PIN entry."
            ),
            BrandSecurityInfo(
                brandDisplayName = "Vivo / iQOO (Funtouch OS 13+)",
                isNativeShutdownLockSupported = true,
                featureName = "Screen lock privacy",
                locationSteps = listOf("Settings > Fingerprint, face and password > Screen lock privacy"),
                explanation = "Protects the power menu and blocks unauthorized power-down when locked."
            ),
            BrandSecurityInfo(
                brandDisplayName = "Pixel, Motorola, Nothing & Sony (Stock Android)",
                isNativeShutdownLockSupported = false,
                featureName = "Kavach Alarm Defense",
                locationSteps = listOf("Kavach Pocket Guard + Instant Max-Volume Siren + Volume Floor Lock"),
                explanation = "Stock Android lacks a native shutdown PIN. Kavach protects by instantly detonating siren upon extraction and blocking volume dampening."
            )
        )
    }

    fun openSecuritySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) { }
        }
    }
}
