package com.kavach.app.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmsFallbackHelper {

    /**
     * Checks if SEND_SMS permission is granted.
     */
    fun hasSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Sends an emergency anti-theft SMS with live Google Maps coordinates
     * directly to the designated emergency phone number.
     * Returns true if successfully handed over to system SMS service.
     */
    suspend fun sendEmergencyLocationSms(
        context: Context,
        recipientPhoneNumber: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        reason: String = "Security Breach"
    ): Boolean = withContext(Dispatchers.IO) {
        val phone = recipientPhoneNumber.trim()
        if (phone.isBlank() || !hasSmsPermission(context)) {
            return@withContext false
        }

        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val message = if (latitude != 0.0 || longitude != 0.0) {
                val mapsUrl = "https://maps.google.com/?q=$latitude,$longitude"
                "🚨 KAVACH ALERT: $reason! Live coordinates: $mapsUrl (Accuracy: ±${accuracyMeters.toInt()}m). Siren active."
            } else {
                "🚨 KAVACH ALERT: $reason! Intruder triggered device alarm. Siren is actively blasting."
            }

            // Divide message in case text exceeds standard 160-char SMS limit
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
