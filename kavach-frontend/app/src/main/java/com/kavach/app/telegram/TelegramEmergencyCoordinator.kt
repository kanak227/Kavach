package com.kavach.app.telegram

import android.content.Context
import com.kavach.app.camera.SilentCameraHelper
import com.kavach.app.data.KavachPreferences
import com.kavach.app.location.LocationHelper
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TelegramEmergencyCoordinator {

    private var activeJob: Job? = null
    private var commandListenerJob: Job? = null
    private var liveLocationJob: Job? = null
    private var liveLocationMessageId: Long = -1L
    private var isAlarmActive = false
    private var smsAlreadySentForThisAlarm = false
    private var connectivityManager: android.net.ConnectivityManager? = null
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private var smsTrackingJob: Job? = null
    private var smsUpdateCounter = 0

    fun isInternetConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    private fun startNetworkWatcher(context: Context) {
        stopNetworkWatcher()
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return
            connectivityManager = cm
            networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onLost(network: android.net.Network) {
                    if (isAlarmActive && !smsAlreadySentForThisAlarm) {
                        CoroutineScope(Dispatchers.IO).launch {
                            triggerSmsFallback(context, "Internet Cut by Intruder")
                        }
                    }
                }
            }
            cm.registerDefaultNetworkCallback(networkCallback!!)
        } catch (_: Exception) {}
    }

    private fun stopNetworkWatcher() {
        try {
            if (networkCallback != null && connectivityManager != null) {
                connectivityManager?.unregisterNetworkCallback(networkCallback!!)
            }
        } catch (_: Exception) {}
        networkCallback = null
        connectivityManager = null
    }

    private fun triggerSmsFallback(context: Context, reason: String) {
        startSmsTrackingLoop(context, reason)
    }

    private fun startSmsTrackingLoop(context: Context, initialReason: String) {
        if (smsTrackingJob?.isActive == true) return
        val prefs = KavachPreferences(context).getInitialSettings()
        val config = prefs.telegramAlert
        if (!config.smsFallbackEnabled || config.emergencyPhoneNumber.isBlank()) return

        val intervalSecs = config.smsIntervalSeconds.coerceIn(30, 600)
        smsAlreadySentForThisAlarm = true
        smsUpdateCounter = 0

        smsTrackingJob = CoroutineScope(Dispatchers.IO).launch {
            // 1. Send immediate first alert SMS
            smsUpdateCounter++
            sendSingleSmsUpdate(context, config.emergencyPhoneNumber, initialReason)

            // 2. Continuous repeating GPS tracking loop while alarm is active
            while (isActive && isAlarmActive) {
                delay(intervalSecs * 1000L)
                if (!isAlarmActive) break
                smsUpdateCounter++
                sendSingleSmsUpdate(context, config.emergencyPhoneNumber, "Live Update #$smsUpdateCounter")
            }
        }
    }

    private suspend fun sendSingleSmsUpdate(context: Context, phone: String, reason: String) {
        try {
            val loc = LocationHelper.getBestLocation(context)
            if (loc != null) {
                com.kavach.app.sms.SmsFallbackHelper.sendEmergencyLocationSms(
                    context = context,
                    recipientPhoneNumber = phone,
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    accuracyMeters = loc.accuracyMeters,
                    reason = reason
                )
            } else {
                com.kavach.app.sms.SmsFallbackHelper.sendEmergencyLocationSms(
                    context = context,
                    recipientPhoneNumber = phone,
                    latitude = 0.0,
                    longitude = 0.0,
                    accuracyMeters = 0f,
                    reason = reason
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val timeFormat = SimpleDateFormat("hh:mm:ss a, dd MMM", Locale.getDefault())

    private fun getCurrentFormattedTime(): String = timeFormat.format(Date())

    /**
     * Triggered when the full alarm detonates.
     * Grabs location, shoots 5 rapid front-camera snapshots, and opens a 2-way Telegram command session.
     */
    fun onAlarmDetonated(context: Context) {
        val prefs = KavachPreferences(context).getInitialSettings()
        val config = prefs.telegramAlert

        val hasTelegram = config.isEnabled && config.chatId.isNotBlank() && config.botToken.isNotBlank()
        val hasSms = config.smsFallbackEnabled && config.emergencyPhoneNumber.isNotBlank()

        if (!hasTelegram && !hasSms) {
            return
        }

        isAlarmActive = true
        smsAlreadySentForThisAlarm = false
        activeJob?.cancel()
        commandListenerJob?.cancel()

        val isOnline = isInternetConnected(context)

        // Offline Trigger: If device is offline when alarm sounds, send SMS immediately!
        if (!isOnline && hasSms) {
            CoroutineScope(Dispatchers.IO).launch {
                triggerSmsFallback(context, "Offline Security Breach")
            }
        } else if (isOnline && hasSms) {
            // Online Trigger: Monitor network in real time so if thief cuts internet later, SMS sends immediately!
            startNetworkWatcher(context)
        }

        if (!hasTelegram) {
            // If user only configured SMS and not Telegram, SMS is dispatched above
            return
        }

        val effectiveToken = config.botToken.trim()
        val targetChatId = config.chatId.trim()

        activeJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val battery = LocationHelper.getBatteryStatus(context)
                val alertTime = getCurrentFormattedTime()

                // Step 1: Instant SOS Breach Notification
                val initialMsg = """
                    🚨 *KAVACH SECURITY BREACH DETECTED!* 🚨
                    
                    Unauthorized physical tampering or pocket extraction triggered the siren!
                    ⏰ *Time:* $alertTime
                    🔋 *Battery:* $battery
                    
                    _Acquiring live GPS & capturing intruder photos..._
                """.trimIndent()

                TelegramService.sendMessage(effectiveToken, targetChatId, initialMsg)

                // Step 2: Dynamic Live Streaming GPS Location (Auto-refreshing interactive map)
                val loc = LocationHelper.getBestLocation(context)
                var telegramDelivered = false
                if (loc != null) {
                    val liveMessageId = TelegramService.sendLiveLocation(
                        botToken = effectiveToken,
                        chatId = targetChatId,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        livePeriodSeconds = 3600 // 1 hour live tracking
                    )

                    if (liveMessageId > 0L) {
                        telegramDelivered = true
                        liveLocationMessageId = liveMessageId
                        val mapMsg = "📡 *Live Streaming GPS Map Activated:*\nTracking beacon updates live in the interactive map above!\n📍 *Maps Link:* ${loc.mapsUrl}\n_Accuracy: ±${loc.accuracyMeters.toInt()}m_"
                        TelegramService.sendMessage(effectiveToken, targetChatId, mapMsg)

                        // Start continuous live location updater while alarm is active
                        startLiveLocationTrackingLoop(context, effectiveToken, targetChatId, liveMessageId)
                    } else {
                        // Fallback to static pin if live location request fails
                        val staticSent = TelegramService.sendLocation(effectiveToken, targetChatId, loc.latitude, loc.longitude)
                        if (staticSent) {
                            telegramDelivered = true
                            val mapMsg = "📍 *Live Google Maps Link:*\n${loc.mapsUrl}\n_Accuracy: ±${loc.accuracyMeters.toInt()}m_"
                            TelegramService.sendMessage(effectiveToken, targetChatId, mapMsg)
                        }
                    }
                } else {
                    TelegramService.sendMessage(
                        effectiveToken,
                        targetChatId,
                        "📍 *Location:* Could not acquire GPS fix (permission not granted or sensors off)."
                    )
                }

                // If Telegram failed to deliver or connection broke, fire SMS fallback immediately
                if (!telegramDelivered && hasSms) {
                    triggerSmsFallback(context, "Security Breach Delivery Fallback")
                }

                // Step 3: Rapid Intruder Burst Sequence
                if (config.intruderPhotosEnabled && SilentCameraHelper.hasCameraPermission(context)) {
                    val burstCount = config.initialBurstCount.coerceIn(1, 10)
                    val uploadJobs = mutableListOf<Job>()

                    SilentCameraHelper.captureBurst(
                        context = context,
                        count = burstCount,
                        intervalMs = 1200L,
                        frontCamera = true,
                        onPhotoReady = { photoBytes, index ->
                            if (!isAlarmActive) return@captureBurst
                            val job = CoroutineScope(Dispatchers.IO).launch {
                                val caption = "🚨 *Intruder Snapshot $index/$burstCount*\n⏰ ${getCurrentFormattedTime()}"
                                TelegramService.sendPhoto(effectiveToken, targetChatId, photoBytes, caption)
                            }
                            uploadJobs.add(job)
                        }
                    )

                    // Strictly await all photo uploads to finish before declaring snapshots finished
                    uploadJobs.joinAll()
                }

                // Step 4: Offer Remote Live Surveillance Commands (Dispatched only AFTER all photos arrive)
                if (isAlarmActive) {
                    val controlsMsg = """
                        📸 *Initial intruder snapshots finished.*
                        
                        You can now control surveillance remotely directly from Telegram:
                        • Tap *Snap Front Photo* or send `/photo`
                        • Tap *Rear Photo* or send `/backphoto`
                        • Tap *Live Location Pin* or send `/loc`
                        • Send `/status` for battery & siren status
                    """.trimIndent()

                    TelegramService.sendMessage(
                        effectiveToken,
                        targetChatId,
                        controlsMsg,
                        includeQuickKeyboard = true
                    )
                }

                // Step 5: Start 2-Way Command Listener while alarm is active
                if (isAlarmActive) {
                    startCommandListener(context, effectiveToken, targetChatId)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Listens for incoming commands from the emergency contact while the alarm is active.
     */
    private fun startCommandListener(context: Context, botToken: String, authorizedChatId: String) {
        commandListenerJob?.cancel()
        commandListenerJob = CoroutineScope(Dispatchers.IO).launch {
            var lastOffset = 0L

            // Clear old backlog first so we only react to fresh commands
            try {
                val backlog = TelegramService.getUpdates(botToken, offset = 0, timeoutSeconds = 1)
                if (backlog.isNotEmpty()) {
                    lastOffset = backlog.maxOf { it.updateId } + 1
                }
            } catch (_: Exception) {}

            while (isActive && isAlarmActive) {
                try {
                    val updates = TelegramService.getUpdates(botToken, offset = lastOffset, timeoutSeconds = 2)
                    for (update in updates) {
                        lastOffset = maxOf(lastOffset, update.updateId + 1)

                        // Strict authentication: only authorized chat can issue remote commands
                        if (update.chatId.trim() != authorizedChatId.trim()) {
                            continue
                        }

                        handleRemoteCommand(context, botToken, authorizedChatId, update.command)
                    }
                } catch (_: Exception) {
                    delay(2000L)
                }
                delay(1000L)
            }
        }
    }

    private suspend fun handleRemoteCommand(
        context: Context,
        botToken: String,
        chatId: String,
        commandText: String
    ) {
        val cmd = commandText.lowercase().trim()

        when {
            cmd == "/photo" || cmd == "photo" || cmd == "snap" || cmd.contains("front") -> {
                if (!SilentCameraHelper.hasCameraPermission(context)) {
                    TelegramService.sendMessage(
                        botToken,
                        chatId,
                        "⚠️ *Camera Permission Required*\n\nCamera access is currently not granted to Kavach on this device. Please open the Kavach app and grant Camera permission in device settings.",
                        includeQuickKeyboard = true
                    )
                    return
                }
                TelegramService.sendMessage(botToken, chatId, "📸 *Capturing front intruder photo now...*")
                val photo = SilentCameraHelper.captureSnapshot(context, frontCamera = true)
                if (photo != null) {
                    TelegramService.sendPhoto(botToken, chatId, photo, "📸 *Live Front Snapshot*\n⏰ ${getCurrentFormattedTime()}")
                } else {
                    TelegramService.sendMessage(
                        botToken,
                        chatId,
                        "⚠️ *Capture Failed:* The camera hardware is currently busy or temporarily unavailable.",
                        includeQuickKeyboard = true
                    )
                }
            }

            cmd == "/backphoto" || cmd == "backphoto" || cmd.contains("back") || cmd.contains("rear") -> {
                if (!SilentCameraHelper.hasCameraPermission(context)) {
                    TelegramService.sendMessage(
                        botToken,
                        chatId,
                        "⚠️ *Camera Permission Required*\n\nCamera access is currently not granted to Kavach on this device. Please open the Kavach app and grant Camera permission in device settings.",
                        includeQuickKeyboard = true
                    )
                    return
                }
                TelegramService.sendMessage(botToken, chatId, "🔄 *Capturing rear camera photo now...*")
                val photo = SilentCameraHelper.captureSnapshot(context, frontCamera = false)
                if (photo != null) {
                    TelegramService.sendPhoto(botToken, chatId, photo, "🔄 *Live Rear Camera View*\n⏰ ${getCurrentFormattedTime()}")
                } else {
                    TelegramService.sendMessage(
                        botToken,
                        chatId,
                        "⚠️ *Capture Failed:* The rear camera hardware is currently busy or temporarily unavailable.",
                        includeQuickKeyboard = true
                    )
                }
            }

            cmd == "/loc" || cmd == "/location" || cmd == "loc" || cmd == "location" || cmd.contains("where") -> {
                if (!LocationHelper.hasLocationPermission(context)) {
                    TelegramService.sendMessage(
                        botToken,
                        chatId,
                        "⚠️ *Location Permission Required*\n\nLocation permission is not granted to Kavach on this device. Please open Kavach on the phone and allow Location access.",
                        includeQuickKeyboard = true
                    )
                    return
                }
                if (!LocationHelper.isLocationEnabled(context)) {
                    TelegramService.sendMessage(
                        botToken,
                        chatId,
                        "⚠️ *Device GPS Switched Off*\n\nLocation service (GPS) is turned off in the phone's Android settings. Please switch on Location in the phone's quick settings shade to receive live GPS pins.",
                        includeQuickKeyboard = true
                    )
                    return
                }
                TelegramService.sendMessage(botToken, chatId, "📍 *Acquiring fresh GPS coordinates...*")
                val loc = LocationHelper.getBestLocation(context)
                if (loc != null) {
                    TelegramService.sendLocation(botToken, chatId, loc.latitude, loc.longitude)
                    TelegramService.sendMessage(
                        botToken,
                        chatId,
                        "📍 *Live Emergency Coordinates:*\n${loc.mapsUrl}\n_Signal accuracy: ±${loc.accuracyMeters.toInt()}m_\n⏰ ${getCurrentFormattedTime()}",
                        includeQuickKeyboard = true
                    )
                } else {
                    TelegramService.sendMessage(
                        botToken,
                        chatId,
                        "⚠️ *GPS Signal Weak:* Unable to lock onto satellite or network location. Retrying shortly.",
                        includeQuickKeyboard = true
                    )
                }
            }

            cmd == "/status" || cmd == "/battery" || cmd == "status" || cmd == "battery" -> {
                val battery = LocationHelper.getBatteryStatus(context)
                val camStatus = if (SilentCameraHelper.hasCameraPermission(context)) "✅ Enabled" else "❌ Missing Permission"
                val locPerm = if (LocationHelper.hasLocationPermission(context)) "✅ Granted" else "❌ Missing Permission"
                val gpsHardware = if (LocationHelper.isLocationEnabled(context)) "✅ Active" else "⚠️ GPS Switched Off"

                val statusMsg = """
                    🛡️ *Kavach Device Status Report*
                    • Siren State: *ACTIVE ALARM 🚨*
                    • Battery Level: *$battery*
                    • Camera Access: *$camStatus*
                    • Location Access: *$locPerm*
                    • Device GPS: *$gpsHardware*
                    • Timestamp: *${getCurrentFormattedTime()}*
                """.trimIndent()
                TelegramService.sendMessage(botToken, chatId, statusMsg, includeQuickKeyboard = true)
            }

            cmd == "/help" || cmd == "help" -> {
                val helpMsg = """
                    🤖 *Kavach Emergency Commands:*
                    • `/photo` — Capture instant front intruder photo
                    • `/backphoto` — Capture instant rear camera photo
                    • `/loc` — Transmit current live GPS coordinates
                    • `/status` — View device battery & security status
                """.trimIndent()
                TelegramService.sendMessage(botToken, chatId, helpMsg, includeQuickKeyboard = true)
            }

            else -> {
                val unknownMsg = """
                    ℹ️ *Command Not Recognized: "$commandText"*

                    Please tap one of the available emergency commands below:
                    • `/photo` — Capture front camera snapshot
                    • `/backphoto` — Capture rear camera snapshot
                    • `/loc` — Request fresh GPS location pin
                    • `/status` — View device status and battery
                """.trimIndent()
                TelegramService.sendMessage(botToken, chatId, unknownMsg, includeQuickKeyboard = true)
            }
        }
    }

    /**
     * Triggered when the owner enters valid PIN or fingerprint to disarm.
     * Stops the command listener and notifies the emergency contact.
     */
    fun onAlarmDeactivated(context: Context) {
        if (!isAlarmActive) return
        isAlarmActive = false

        activeJob?.cancel()
        activeJob = null

        commandListenerJob?.cancel()
        commandListenerJob = null

        liveLocationJob?.cancel()
        liveLocationJob = null
        liveLocationMessageId = -1L
        stopNetworkWatcher()
        smsTrackingJob?.cancel()
        smsTrackingJob = null
        smsAlreadySentForThisAlarm = false
        smsUpdateCounter = 0

        val prefs = KavachPreferences(context).getInitialSettings()
        val config = prefs.telegramAlert

        if (config.isEnabled && config.chatId.isNotBlank() && config.botToken.isNotBlank()) {
            val effectiveToken = config.botToken.trim()
            val targetChatId = config.chatId.trim()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val disarmMsg = """
                        ✅ *DEVICE SAFELY DISARMED*
                        
                        The phone owner has successfully unlocked the shield with PIN or fingerprint.
                        Remote emergency surveillance session is now closed.
                        ⏰ *Time:* ${getCurrentFormattedTime()}
                    """.trimIndent()

                    TelegramService.sendMessage(effectiveToken, targetChatId, disarmMsg)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Tests the Telegram integration by sending a verified test alert to the configured Chat ID.
     */
    suspend fun sendTestAlert(
        context: Context,
        botToken: String,
        chatId: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val effectiveToken = botToken.trim()
        val targetChatId = chatId.trim()

        if (effectiveToken.isBlank()) {
            return@withContext Pair(false, "Please enter your Telegram Bot Token from @BotFather first.")
        }

        if (targetChatId.isBlank()) {
            return@withContext Pair(false, "Please enter your Telegram Chat ID.")
        }

        // 1. Verify token
        val (tokenOk, tokenMsg) = TelegramService.testBotConnection(effectiveToken)
        if (!tokenOk) {
            return@withContext Pair(false, "Invalid Bot: $tokenMsg")
        }

        // 2. Send test message
        val testText = """
            🛡️ *Kavach Emergency Alert Test*
            
            Congratulations! Your phone is now successfully linked with Kavach.
            When an alarm detonates on your phone, emergency GPS coordinates and 5 intruder face snapshots will be automatically transmitted right here!
            
            ⏰ *Test Time:* ${getCurrentFormattedTime()}
            🔋 *Phone Battery:* ${LocationHelper.getBatteryStatus(context)}
            
            ℹ️ *Remote Controls Activation:*
            The interactive buttons below (`/photo`, `/backphoto`, `/loc`, `/status`) are live emergency surveillance controls. They automatically activate to capture real-time updates whenever the phone's alarm or siren is triggered.
        """.trimIndent()

        val sent = TelegramService.sendMessage(effectiveToken, targetChatId, testText, includeQuickKeyboard = true)
        if (!sent) {
            return@withContext Pair(
                false,
                "Could not deliver to Chat ID $targetChatId. Did you tap START in Telegram with $tokenMsg?"
            )
        }

        // 3. Send test location if permitted
        if (LocationHelper.hasLocationPermission(context)) {
            val loc = LocationHelper.getBestLocation(context)
            if (loc != null) {
                TelegramService.sendLocation(effectiveToken, targetChatId, loc.latitude, loc.longitude)
            }
        }

        Pair(true, "Test alert sent successfully to $tokenMsg!")
    }

    /**
     * Periodically acquires fresh GPS coordinates and edits the Telegram Live Location message,
     * providing true real-time path tracking without spamming chat with new messages.
     */
    private fun startLiveLocationTrackingLoop(
        context: Context,
        botToken: String,
        chatId: String,
        messageId: Long
    ) {
        liveLocationJob?.cancel()
        liveLocationJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && isAlarmActive) {
                delay(20000L) // Refresh every 20 seconds
                if (!isAlarmActive) break
                try {
                    val freshLoc = LocationHelper.getBestLocation(context)
                    if (freshLoc != null) {
                        val edited = TelegramService.editMessageLiveLocation(
                            botToken = botToken,
                            chatId = chatId,
                            messageId = messageId,
                            latitude = freshLoc.latitude,
                            longitude = freshLoc.longitude
                        )
                        if (!edited && !isInternetConnected(context)) {
                            triggerSmsFallback(context, "Internet Cut by Intruder")
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

}
