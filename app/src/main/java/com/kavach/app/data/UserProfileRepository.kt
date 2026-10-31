package com.kavach.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userProfileDataStore by preferencesDataStore(name = "kavach_user_profile")

data class UserProfile(
    val fullName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = "",
    val bloodGroup: String = "",
    val homeAddress: String = "",
    val notes: String = ""
)

data class GuardUsageSample(
    val hourOfDay: Int, // 9 to 21 (9 AM to 9 PM)
    val mode: DetectionMode,
    val usageMinutes: Int
)

class UserProfileRepository(private val context: Context) {

    private val sharedPrefs = context.getSharedPreferences("kavach_profile_cache", Context.MODE_PRIVATE)

    companion object {
        val KEY_FULL_NAME = stringPreferencesKey("profile_full_name")
        val KEY_EMAIL = stringPreferencesKey("profile_email")
        val KEY_PHONE = stringPreferencesKey("profile_phone")
        val KEY_EMERGENCY_NAME = stringPreferencesKey("profile_emergency_name")
        val KEY_EMERGENCY_PHONE = stringPreferencesKey("profile_emergency_phone")
        val KEY_BLOOD_GROUP = stringPreferencesKey("profile_blood_group")
        val KEY_ADDRESS = stringPreferencesKey("profile_address")
        val KEY_NOTES = stringPreferencesKey("profile_notes")
    }

    fun getInitialProfile(): UserProfile {
        return UserProfile(
            fullName = sharedPrefs.getString("full_name", "") ?: "",
            email = sharedPrefs.getString("email", "") ?: "",
            phoneNumber = sharedPrefs.getString("phone", "") ?: "",
            emergencyContactName = sharedPrefs.getString("emergency_name", "") ?: "",
            emergencyContactPhone = sharedPrefs.getString("emergency_phone", "") ?: "",
            bloodGroup = sharedPrefs.getString("blood_group", "") ?: "",
            homeAddress = sharedPrefs.getString("address", "") ?: "",
            notes = sharedPrefs.getString("notes", "") ?: ""
        )
    }

    val userProfileFlow: Flow<UserProfile> = context.userProfileDataStore.data.map { prefs ->
        UserProfile(
            fullName = prefs[KEY_FULL_NAME] ?: sharedPrefs.getString("full_name", "") ?: "",
            email = prefs[KEY_EMAIL] ?: sharedPrefs.getString("email", "") ?: "",
            phoneNumber = prefs[KEY_PHONE] ?: sharedPrefs.getString("phone", "") ?: "",
            emergencyContactName = prefs[KEY_EMERGENCY_NAME] ?: sharedPrefs.getString("emergency_name", "") ?: "",
            emergencyContactPhone = prefs[KEY_EMERGENCY_PHONE] ?: sharedPrefs.getString("emergency_phone", "") ?: "",
            bloodGroup = prefs[KEY_BLOOD_GROUP] ?: sharedPrefs.getString("blood_group", "") ?: "",
            homeAddress = prefs[KEY_ADDRESS] ?: sharedPrefs.getString("address", "") ?: "",
            notes = prefs[KEY_NOTES] ?: sharedPrefs.getString("notes", "") ?: ""
        )
    }

    suspend fun saveProfile(profile: UserProfile) {
        sharedPrefs.edit()
            .putString("full_name", profile.fullName)
            .putString("email", profile.email)
            .putString("phone", profile.phoneNumber)
            .putString("emergency_name", profile.emergencyContactName)
            .putString("emergency_phone", profile.emergencyContactPhone)
            .putString("blood_group", profile.bloodGroup)
            .putString("address", profile.homeAddress)
            .putString("notes", profile.notes)
            .apply()

        context.userProfileDataStore.edit { prefs ->
            prefs[KEY_FULL_NAME] = profile.fullName
            prefs[KEY_EMAIL] = profile.email
            prefs[KEY_PHONE] = profile.phoneNumber
            prefs[KEY_EMERGENCY_NAME] = profile.emergencyContactName
            prefs[KEY_EMERGENCY_PHONE] = profile.emergencyContactPhone
            prefs[KEY_BLOOD_GROUP] = profile.bloodGroup
            prefs[KEY_ADDRESS] = profile.homeAddress
            prefs[KEY_NOTES] = profile.notes
        }
    }

    /**
     * Records guard mode usage for daily statistics.
     * Stored in shared preferences for today's hourly usage (9 AM to 9 PM).
     */
    fun recordModeUsage(hour: Int, mode: DetectionMode, durationMinutes: Int = 1) {
        if (hour in 9..21) {
            val key = "usage_${todayDateKey()}_${hour}_${mode.name}"
            val existing = sharedPrefs.getInt(key, 0)
            sharedPrefs.edit().putInt(key, existing + durationMinutes).apply()
        }
    }

    fun getTodayHourlyUsage(): Map<Int, Map<DetectionMode, Int>> {
        val result = mutableMapOf<Int, MutableMap<DetectionMode, Int>>()
        val dateKey = todayDateKey()
        for (hour in 9..21) {
            val hourMap = mutableMapOf<DetectionMode, Int>()
            for (mode in DetectionMode.values()) {
                val key = "usage_${dateKey}_${hour}_${mode.name}"
                val minutes = sharedPrefs.getInt(key, 0)
                hourMap[mode] = minutes
            }
            result[hour] = hourMap
        }
        return result
    }

    private fun todayDateKey(): String {
        val cal = java.util.Calendar.getInstance()
        return "${cal.get(java.util.Calendar.YEAR)}_${cal.get(java.util.Calendar.MONTH)}_${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
    }
}
