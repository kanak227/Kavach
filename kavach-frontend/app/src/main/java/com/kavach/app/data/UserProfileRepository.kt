package com.kavach.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userProfileDataStore by preferencesDataStore(name = "kavach_user_profile")

data class SosContact(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val phone: String = ""
)

data class UserProfile(
    val fullName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = "",
    val bloodGroup: String = "",
    val homeAddress: String = "",
    val notes: String = "",
    val profilePicturePath: String = "",
    val sosContacts: List<SosContact> = emptyList(),
    val sleepScheduleAnswer: String = "", // "Yes", "Maybe", "No"
    val sleepScheduleStart: String = "",
    val sleepScheduleEnd: String = "",
    val password: String = ""
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
        val KEY_PICTURE_PATH = stringPreferencesKey("profile_picture_path")
        val KEY_SOS_CONTACTS = stringPreferencesKey("profile_sos_contacts")
        val KEY_SLEEP_ANSWER = stringPreferencesKey("profile_sleep_answer")
        val KEY_SLEEP_START = stringPreferencesKey("profile_sleep_start")
        val KEY_SLEEP_END = stringPreferencesKey("profile_sleep_end")
        val KEY_PASSWORD = stringPreferencesKey("profile_password")

        fun serializeSosContacts(contacts: List<SosContact>): String {
            val arr = org.json.JSONArray()
            for (c in contacts) {
                val obj = org.json.JSONObject()
                obj.put("id", c.id)
                obj.put("name", c.name)
                obj.put("phone", c.phone)
                arr.put(obj)
            }
            return arr.toString()
        }

        fun deserializeSosContacts(jsonStr: String): List<SosContact> {
            if (jsonStr.isBlank()) return emptyList()
            return try {
                val arr = org.json.JSONArray(jsonStr)
                val list = mutableListOf<SosContact>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        SosContact(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            name = obj.optString("name", ""),
                            phone = obj.optString("phone", "")
                        )
                    )
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun isSignedUp(): Boolean {
        return sharedPrefs.getBoolean("is_signed_up", false)
    }

    fun setSignedUp(signedUp: Boolean) {
        sharedPrefs.edit().putBoolean("is_signed_up", signedUp).apply()
    }

    fun getSavedPassword(): String {
        return sharedPrefs.getString("profile_password", "") ?: ""
    }

    fun verifyPassword(phone: String, pass: String): Boolean {
        val savedPhone = sharedPrefs.getString("phone", "") ?: ""
        val savedPass = getSavedPassword()
        if (savedPass.isBlank()) return false
        val cleanPhoneInput = phone.replace(Regex("[^0-9]"), "")
        val cleanSavedPhone = savedPhone.replace(Regex("[^0-9]"), "")
        val phoneMatches = cleanPhoneInput.isNotBlank() && (
            cleanPhoneInput == cleanSavedPhone ||
            (cleanPhoneInput.length >= 10 && cleanSavedPhone.endsWith(cleanPhoneInput.takeLast(10))) ||
            (cleanSavedPhone.length >= 10 && cleanPhoneInput.endsWith(cleanSavedPhone.takeLast(10)))
        )
        return phoneMatches && savedPass == pass
    }


    fun getInitialProfile(): UserProfile {
        val sosRaw = sharedPrefs.getString("sos_contacts", "") ?: ""
        var contacts = deserializeSosContacts(sosRaw)
        val legacyName = sharedPrefs.getString("emergency_name", "") ?: ""
        val legacyPhone = sharedPrefs.getString("emergency_phone", "") ?: ""
        if (contacts.isEmpty() && (legacyName.isNotBlank() || legacyPhone.isNotBlank())) {
            contacts = listOf(SosContact(name = legacyName, phone = legacyPhone))
        }

        return UserProfile(
            fullName = sharedPrefs.getString("full_name", "") ?: "",
            email = sharedPrefs.getString("email", "") ?: "",
            phoneNumber = sharedPrefs.getString("phone", "") ?: "",
            emergencyContactName = legacyName,
            emergencyContactPhone = legacyPhone,
            bloodGroup = sharedPrefs.getString("blood_group", "") ?: "",
            homeAddress = sharedPrefs.getString("address", "") ?: "",
            notes = sharedPrefs.getString("notes", "") ?: "",
            profilePicturePath = sharedPrefs.getString("profile_picture_path", "") ?: "",
            sosContacts = contacts,
            sleepScheduleAnswer = sharedPrefs.getString("sleep_schedule_answer", "") ?: "",
            sleepScheduleStart = sharedPrefs.getString("sleep_schedule_start", "") ?: "",
            sleepScheduleEnd = sharedPrefs.getString("sleep_schedule_end", "") ?: "",
            password = sharedPrefs.getString("profile_password", "") ?: ""
        )
    }

    val userProfileFlow: Flow<UserProfile> = context.userProfileDataStore.data.map { prefs ->
        val legacyName = prefs[KEY_EMERGENCY_NAME] ?: sharedPrefs.getString("emergency_name", "") ?: ""
        val legacyPhone = prefs[KEY_EMERGENCY_PHONE] ?: sharedPrefs.getString("emergency_phone", "") ?: ""
        val sosRaw = prefs[KEY_SOS_CONTACTS] ?: sharedPrefs.getString("sos_contacts", "") ?: ""
        var contacts = deserializeSosContacts(sosRaw)
        if (contacts.isEmpty() && (legacyName.isNotBlank() || legacyPhone.isNotBlank())) {
            contacts = listOf(SosContact(name = legacyName, phone = legacyPhone))
        }

        UserProfile(
            fullName = prefs[KEY_FULL_NAME] ?: sharedPrefs.getString("full_name", "") ?: "",
            email = prefs[KEY_EMAIL] ?: sharedPrefs.getString("email", "") ?: "",
            phoneNumber = prefs[KEY_PHONE] ?: sharedPrefs.getString("phone", "") ?: "",
            emergencyContactName = legacyName,
            emergencyContactPhone = legacyPhone,
            bloodGroup = prefs[KEY_BLOOD_GROUP] ?: sharedPrefs.getString("blood_group", "") ?: "",
            homeAddress = prefs[KEY_ADDRESS] ?: sharedPrefs.getString("address", "") ?: "",
            notes = prefs[KEY_NOTES] ?: sharedPrefs.getString("notes", "") ?: "",
            profilePicturePath = prefs[KEY_PICTURE_PATH] ?: sharedPrefs.getString("profile_picture_path", "") ?: "",
            sosContacts = contacts,
            sleepScheduleAnswer = prefs[KEY_SLEEP_ANSWER] ?: sharedPrefs.getString("sleep_schedule_answer", "") ?: "",
            sleepScheduleStart = prefs[KEY_SLEEP_START] ?: sharedPrefs.getString("sleep_schedule_start", "") ?: "",
            sleepScheduleEnd = prefs[KEY_SLEEP_END] ?: sharedPrefs.getString("sleep_schedule_end", "") ?: "",
            password = prefs[KEY_PASSWORD] ?: sharedPrefs.getString("profile_password", "") ?: ""
        )
    }

    suspend fun saveProfile(profile: UserProfile) {
        val primaryEmergencyName = profile.sosContacts.firstOrNull()?.name ?: profile.emergencyContactName
        val primaryEmergencyPhone = profile.sosContacts.firstOrNull()?.phone ?: profile.emergencyContactPhone
        val sosJson = serializeSosContacts(profile.sosContacts)

        sharedPrefs.edit()
            .putString("full_name", profile.fullName)
            .putString("email", profile.email)
            .putString("phone", profile.phoneNumber)
            .putString("emergency_name", primaryEmergencyName)
            .putString("emergency_phone", primaryEmergencyPhone)
            .putString("blood_group", profile.bloodGroup)
            .putString("address", profile.homeAddress)
            .putString("notes", profile.notes)
            .putString("profile_picture_path", profile.profilePicturePath)
            .putString("sos_contacts", sosJson)
            .putString("sleep_schedule_answer", profile.sleepScheduleAnswer)
            .putString("sleep_schedule_start", profile.sleepScheduleStart)
            .putString("sleep_schedule_end", profile.sleepScheduleEnd)
            .putString("profile_password", if (profile.password.isNotBlank()) profile.password else sharedPrefs.getString("profile_password", "") ?: "")
            .apply()

        context.userProfileDataStore.edit { prefs ->
            prefs[KEY_FULL_NAME] = profile.fullName
            prefs[KEY_EMAIL] = profile.email
            prefs[KEY_PHONE] = profile.phoneNumber
            prefs[KEY_EMERGENCY_NAME] = primaryEmergencyName
            prefs[KEY_EMERGENCY_PHONE] = primaryEmergencyPhone
            prefs[KEY_BLOOD_GROUP] = profile.bloodGroup
            prefs[KEY_ADDRESS] = profile.homeAddress
            prefs[KEY_NOTES] = profile.notes
            prefs[KEY_PICTURE_PATH] = profile.profilePicturePath
            prefs[KEY_SOS_CONTACTS] = sosJson
            prefs[KEY_SLEEP_ANSWER] = profile.sleepScheduleAnswer
            prefs[KEY_SLEEP_START] = profile.sleepScheduleStart
            prefs[KEY_SLEEP_END] = profile.sleepScheduleEnd
            prefs[KEY_PASSWORD] = if (profile.password.isNotBlank()) profile.password else (sharedPrefs.getString("profile_password", "") ?: "")
        }
    }

    /**
     * Records guard mode usage in seconds for live real-time statistics across all 24 hours.
     */
    fun recordModeUsageSeconds(hour: Int, mode: DetectionMode, seconds: Int = 1) {
        if (hour in 0..23) {
            val key = "usage_sec_${todayDateKey()}_${hour}_${mode.name}"
            val existing = sharedPrefs.getInt(key, 0)
            sharedPrefs.edit().putInt(key, existing + seconds).apply()
        }
    }

    /**
     * Records guard mode usage in minutes (for legacy compatibility).
     */
    fun recordModeUsage(hour: Int, mode: DetectionMode, durationMinutes: Int = 1) {
        recordModeUsageSeconds(hour, mode, durationMinutes * 60)
    }

    /**
     * Returns total seconds protected per mode for each hour 0..23 today.
     */
    fun getTodayHourlyUsage(): Map<Int, Map<DetectionMode, Int>> {
        val result = mutableMapOf<Int, MutableMap<DetectionMode, Int>>()
        val dateKey = todayDateKey()
        for (hour in 0..23) {
            val hourMap = mutableMapOf<DetectionMode, Int>()
            for (mode in DetectionMode.values()) {
                val secKey = "usage_sec_${dateKey}_${hour}_${mode.name}"
                val oldMinKey = "usage_${dateKey}_${hour}_${mode.name}"
                var seconds = sharedPrefs.getInt(secKey, -1)
                if (seconds == -1) {
                    val oldMinutes = sharedPrefs.getInt(oldMinKey, 0)
                    seconds = oldMinutes * 60
                }
                hourMap[mode] = seconds
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
