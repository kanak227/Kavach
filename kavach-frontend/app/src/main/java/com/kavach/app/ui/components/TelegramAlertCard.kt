package com.kavach.app.ui.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kavach.app.camera.SilentCameraHelper
import com.kavach.app.data.TelegramAlertConfig
import com.kavach.app.location.LocationHelper
import com.kavach.app.telegram.TelegramEmergencyCoordinator
import com.kavach.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TelegramAlertCard(
    config: TelegramAlertConfig,
    onConfigChanged: (TelegramAlertConfig) -> Unit,
    colors: KavachColors = LocalKavachColors.current,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val activeAccent = if (colors.isDark) KavachGold else Color(0xFF00758F)

    var hasLocPermission by remember { mutableStateOf(LocationHelper.hasLocationPermission(context)) }
    var isLocServiceEnabled by remember { mutableStateOf(LocationHelper.isLocationEnabled(context)) }
    var hasCamPermission by remember { mutableStateOf(SilentCameraHelper.hasCameraPermission(context)) }
    var hasSmsPermission by remember { mutableStateOf(com.kavach.app.sms.SmsFallbackHelper.hasSmsPermission(context)) }

    // Continuous real-time permission and hardware status synchronization
    LaunchedEffect(Unit) {
        while (true) {
            hasLocPermission = LocationHelper.hasLocationPermission(context)
            isLocServiceEnabled = LocationHelper.isLocationEnabled(context)
            hasCamPermission = SilentCameraHelper.hasCameraPermission(context)
            hasSmsPermission = com.kavach.app.sms.SmsFallbackHelper.hasSmsPermission(context)
            kotlinx.coroutines.delay(1000L)
        }
    }

    // Immediate sync on lifecycle resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocPermission = LocationHelper.hasLocationPermission(context)
                isLocServiceEnabled = LocationHelper.isLocationEnabled(context)
                hasCamPermission = SilentCameraHelper.hasCameraPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Direct, crash-proof 16-bit permission requests
    fun requestLocationPermission() {
        activity?.let {
            ActivityCompat.requestPermissions(
                it,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                102
            )
        }
    }

    fun requestCameraPermission() {
        activity?.let {
            ActivityCompat.requestPermissions(
                it,
                arrayOf(Manifest.permission.CAMERA),
                103
            )
        }
    }

    fun requestSmsPermission() {
        activity?.let {
            ActivityCompat.requestPermissions(
                it,
                arrayOf(Manifest.permission.SEND_SMS),
                104
            )
        }
    }

    fun requestAllSecurityPermissions() {
        val list = mutableListOf<String>()
        if (!hasLocPermission) {
            list.add(Manifest.permission.ACCESS_FINE_LOCATION)
            list.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (!hasCamPermission) {
            list.add(Manifest.permission.CAMERA)
        }
        if (list.isNotEmpty()) {
            activity?.let {
                ActivityCompat.requestPermissions(it, list.toTypedArray(), 102)
            }
        }
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    // Local state for Chat ID and Bot Token with explicit Save confirmation
    val focusManager = LocalFocusManager.current
    var inputChatId by remember(config.chatId) { mutableStateOf(config.chatId) }
    var inputBotToken by remember(config.botToken) { mutableStateOf(config.botToken) }
    var inputPhoneNumber by rememberSaveable { mutableStateOf(config.emergencyPhoneNumber) }
    var isPhoneInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(config.emergencyPhoneNumber) {
        if (!isPhoneInitialized || inputPhoneNumber.isEmpty()) {
            inputPhoneNumber = config.emergencyPhoneNumber
            isPhoneInitialized = true
        }
    }

    LaunchedEffect(inputPhoneNumber) {
        if (isPhoneInitialized && inputPhoneNumber != config.emergencyPhoneNumber) {
            delay(500)
            onConfigChanged(config.copy(emergencyPhoneNumber = inputPhoneNumber.trim()))
        }
    }
    var isCredentialsSaved by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testStatusText by remember { mutableStateOf<String?>(null) }
    var testStatusIsSuccess by remember { mutableStateOf(false) }
    var showTimelineGuide by remember { mutableStateOf(false) }

    val hasUnsavedChanges = inputChatId != config.chatId || inputBotToken != config.botToken
    val isConfigured = config.chatId.isNotBlank() && config.botToken.isNotBlank() && !hasUnsavedChanges

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(
                1.dp,
                if (config.isEnabled) activeAccent.copy(alpha = 0.6f) else colors.cardBorder,
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        // Master Toggle Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (config.isEnabled) activeAccent.copy(alpha = 0.2f) else colors.cardBorder.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = null,
                        tint = if (config.isEnabled) activeAccent else colors.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Telegram Emergency Dispatch",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Real-Time GPS & Intruder Evidence",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                }
            }

            Switch(
                checked = config.isEnabled,
                onCheckedChange = { isChecked ->
                    onConfigChanged(config.copy(isEnabled = isChecked))
                    if (isChecked) {
                        requestAllSecurityPermissions()
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = activeAccent,
                    checkedTrackColor = activeAccent.copy(alpha = 0.35f),
                    uncheckedThumbColor = colors.textMuted,
                    uncheckedTrackColor = colors.cardBorder
                )
            )
        }

        AnimatedVisibility(
            visible = config.isEnabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                HorizontalDivider(color = colors.cardBorder)
                Spacer(modifier = Modifier.height(14.dp))

                // Overview note
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = activeAccent.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, activeAccent.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = activeAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "When an alarm detonates, Kavach automatically transmits your live GPS coordinates, Google Maps link, and 5 intruder face snapshots directly to your Telegram. You can then request live photos anytime on demand.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Security Permissions Alert Banner (if any required permission is missing)
                val isAnyPermissionMissing = !hasLocPermission || (!hasCamPermission && config.intruderPhotosEnabled)
                if (isAnyPermissionMissing) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF332010),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.WarningAmber,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Permissions Required for Emergency Dispatch",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Location and Camera access are required to track your device and photograph unauthorized users during an alarm.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFDE68A),
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { requestAllSecurityPermissions() },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF59E0B),
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Icon(Icons.Outlined.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ENABLE SECURITY PERMISSIONS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Field 1: Telegram Bot Token
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "1. TELEGRAM BOT TOKEN",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = activeAccent,
                        letterSpacing = 1.sp
                    )
                    Row(
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/BotFather"))
                            context.startActivity(intent)
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Create bot via @BotFather",
                            style = MaterialTheme.typography.labelSmall,
                            color = activeAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = activeAccent,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = inputBotToken,
                    onValueChange = {
                        inputBotToken = it.trim()
                        isCredentialsSaved = false
                    },
                    placeholder = { Text("Paste your Bot Token here", color = colors.textMuted) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Outlined.Key, contentDescription = null, tint = activeAccent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = activeAccent,
                        unfocusedBorderColor = colors.cardBorder,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedContainerColor = colors.background,
                        unfocusedContainerColor = colors.background
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Field 2: Recipient Chat ID
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "2. RECIPIENT CHAT ID",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = activeAccent,
                        letterSpacing = 1.sp
                    )
                    Row(
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/userinfobot"))
                            context.startActivity(intent)
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Find ID on @userinfobot",
                            style = MaterialTheme.typography.labelSmall,
                            color = activeAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = activeAccent,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = inputChatId,
                    onValueChange = {
                        inputChatId = it.trim()
                        isCredentialsSaved = false
                    },
                    placeholder = { Text("e.g. 5692272225", color = colors.textMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    leadingIcon = {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = activeAccent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = activeAccent,
                        unfocusedBorderColor = colors.cardBorder,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedContainerColor = colors.background,
                        unfocusedContainerColor = colors.background
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Unified Confirm & Save Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        if (isCredentialsSaved || (isConfigured && !hasUnsavedChanges)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF22C55E),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Credentials active and verified",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF22C55E),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = activeAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (hasUnsavedChanges) "Unsaved changes ready to save" else "Enter Token and Chat ID above",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                    maxLines = 2
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            onConfigChanged(
                                config.copy(
                                    chatId = inputChatId.trim(),
                                    botToken = inputBotToken.trim()
                                )
                            )
                            isCredentialsSaved = true
                        },
                        enabled = inputChatId.isNotBlank() && inputBotToken.isNotBlank() && hasUnsavedChanges,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = activeAccent,
                            contentColor = if (colors.isDark) Color.Black else Color.White,
                            disabledContainerColor = colors.background,
                            disabledContentColor = colors.textMuted
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = if (hasUnsavedChanges) Icons.Outlined.Save else Icons.Outlined.Done,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasUnsavedChanges) "SAVE CREDENTIALS" else "SAVED",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Setup Guide Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.background,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                contentDescription = null,
                                tint = activeAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Setup Guide (Takes 1 Minute)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "1. Create your Bot on Telegram:\n" +
                                   "   • Open @BotFather on Telegram and tap START.\n" +
                                   "   • Send /newbot, give it a name, and choose a username ending in \"bot\".\n" +
                                   "   • Copy the HTTP API token @BotFather gives you and paste it in Field 1 above.\n\n" +
                                   "2. Authorize your Bot:\n" +
                                   "   • Open your newly created bot on Telegram and tap START so it can message you.\n\n" +
                                   "3. Connect your Chat ID:\n" +
                                   "   • Enter your numeric Chat ID (from @userinfobot) in Field 2.\n" +
                                   "   • Tap \"SAVE CREDENTIALS\", then tap \"SEND TEST ALERT\" to confirm delivery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Intruder Photo Snapshots Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.background)
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                        .clickable {
                            val next = !config.intruderPhotosEnabled
                            onConfigChanged(config.copy(intruderPhotosEnabled = next))
                            if (next && !hasCamPermission) requestCameraPermission()
                        }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            tint = if (config.intruderPhotosEnabled) activeAccent else colors.textMuted,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Intruder Evidence Capture",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Captures a 5-photo burst sequence upon alarm trigger",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted
                            )
                        }
                    }

                    Switch(
                        checked = config.intruderPhotosEnabled,
                        onCheckedChange = { isChecked ->
                            onConfigChanged(config.copy(intruderPhotosEnabled = isChecked))
                            if (isChecked && !hasCamPermission) requestCameraPermission()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = activeAccent,
                            checkedTrackColor = activeAccent.copy(alpha = 0.35f),
                            uncheckedThumbColor = colors.textMuted,
                            uncheckedTrackColor = colors.cardBorder
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Permission & Hardware Status Badges (Realtime status sync)
                val isLocationFullyReady = hasLocPermission && isLocServiceEnabled
                val isCameraFullyReady = hasCamPermission

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Location status
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                when {
                                    !hasLocPermission -> requestLocationPermission()
                                    !isLocServiceEnabled -> {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                        } catch (_: Exception) {
                                            openAppSettings()
                                        }
                                    }
                                    else -> openAppSettings()
                                }
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isLocationFullyReady) Color(0xFF103322) else Color(0xFF332010),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isLocationFullyReady) Color(0xFF22C55E) else Color(0xFFF59E0B)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isLocationFullyReady) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = if (isLocationFullyReady) Color(0xFF22C55E) else Color(0xFFF59E0B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    !hasLocPermission -> "Grant Location"
                                    !isLocServiceEnabled -> "Turn On GPS"
                                    else -> "Location Active"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isLocationFullyReady) Color(0xFF22C55E) else Color(0xFFF59E0B)
                            )
                        }
                    }

                    // Camera status
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (!hasCamPermission) {
                                    requestCameraPermission()
                                } else {
                                    openAppSettings()
                                }
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCameraFullyReady) Color(0xFF103322) else Color(0xFF332010),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isCameraFullyReady) Color(0xFF22C55E) else Color(0xFFF59E0B)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isCameraFullyReady) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = if (isCameraFullyReady) Color(0xFF22C55E) else Color(0xFFF59E0B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isCameraFullyReady) "Camera Active" else "Grant Camera",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isCameraFullyReady) Color(0xFF22C55E) else Color(0xFFF59E0B)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tip: Badges update in real-time. Tap any badge to grant permissions or switch on GPS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Timeline Explanation Accordion
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showTimelineGuide = !showTimelineGuide }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Surveillance Flow & Remote Commands",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = activeAccent
                    )
                    Icon(
                        imageVector = if (showTimelineGuide) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        tint = activeAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(visible = showTimelineGuide) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.background,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "• Detonation (0s):\n" +
                                       "   Kavach instantly sends an emergency SOS message with device battery level, timestamp, and an interactive Google Maps GPS pin.\n\n" +
                                       "• Photo Burst Sequence:\n" +
                                       "   A rapid 5-photo burst is captured in a single hardware session with continuous Auto-Exposure to ensure crisp, well-lit facial evidence.\n\n" +
                                       "• Live Surveillance Controls:\n" +
                                       "   While the siren is actively sounding, Kavach opens an authenticated remote command listener:\n" +
                                       "   - Tap [📸 Snap Front Photo] (or send /photo) to request a new live photo.\n" +
                                       "   - Tap [🔄 Rear Photo] (or send /backphoto) to view surrounding environment.\n" +
                                       "   - Tap [📍 Live Location Pin] (or send /loc) to fetch updated GPS coordinates.\n" +
                                       "   - Tap [🔋 Status & Battery] (or send /status) to inspect battery and connection.\n" +
                                       "   Note: These remote buttons are live while the alarm is active, remaining dormant during standby to preserve battery.\n\n" +
                                       "• Disarm:\n" +
                                       "   When you unlock your phone with your PIN or fingerprint, the surveillance session closes and sends a final confirmation to your chat.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Send Test Alert Button
                Button(
                    onClick = {
                        isTesting = true
                        testStatusText = null
                        coroutineScope.launch {
                            val (success, msg) = TelegramEmergencyCoordinator.sendTestAlert(
                                context = context,
                                botToken = config.botToken.ifBlank { inputBotToken },
                                chatId = config.chatId.ifBlank { inputChatId }
                            )
                            isTesting = false
                            testStatusIsSuccess = success
                            testStatusText = msg
                        }
                    },
                    enabled = !isTesting && (config.chatId.isNotBlank() || inputChatId.isNotBlank()),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = activeAccent,
                        contentColor = if (colors.isDark) Color.Black else Color.White
                    )
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = if (colors.isDark) Color.Black else Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifying Connection...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SEND TEST ALERT", fontWeight = FontWeight.Bold)
                    }
                }

                // Test Result Banner
                testStatusText?.let { status ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (testStatusIsSuccess) Color(0xFF103322) else Color(0xFF3B1212),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (testStatusIsSuccess) Color(0xFF22C55E) else Color(0xFFEF4444)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (testStatusIsSuccess) Color(0xFF22C55E) else Color(0xFFEF4444)
                            )
                            val botMatch = Regex("@([A-Za-z0-9_]+)").find(status)?.groupValues?.get(1)
                            if (!testStatusIsSuccess && botMatch != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$botMatch"))
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFEF4444),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open @$botMatch in Telegram & Tap START", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = colors.cardBorder)
        Spacer(modifier = Modifier.height(14.dp))

        // === Offline SMS Fallback Configuration ===
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (colors.isDark) Color(0xFF131A26) else Color(0xFFF1F5F9))
                .border(1.dp, if (config.smsFallbackEnabled) activeAccent.copy(alpha = 0.5f) else colors.cardBorder, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Sms,
                            contentDescription = null,
                            tint = if (config.smsFallbackEnabled) activeAccent else colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SMS Emergency Fallback",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Sends live GPS coordinates via SMS if internet is disconnected or thief turns off mobile data",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        fontSize = 11.5.sp
                    )
                }
                Switch(
                    checked = config.smsFallbackEnabled,
                    onCheckedChange = { checked ->
                        if (checked && !hasSmsPermission) {
                            requestSmsPermission()
                        }
                        onConfigChanged(config.copy(smsFallbackEnabled = checked))
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = activeAccent,
                        checkedTrackColor = activeAccent.copy(alpha = 0.4f)
                    )
                )
            }

            if (config.smsFallbackEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = inputPhoneNumber,
                    onValueChange = { newValue ->
                        inputPhoneNumber = newValue
                    },
                    label = { Text("Emergency Contact Phone Number") },
                    placeholder = { Text("+91 9876543210") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Phone, contentDescription = null, tint = activeAccent)
                    },
                    trailingIcon = {
                        if (inputPhoneNumber.isNotBlank()) {
                            IconButton(onClick = {
                                inputPhoneNumber = ""
                                onConfigChanged(config.copy(emergencyPhoneNumber = ""))
                            }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = colors.textMuted)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onConfigChanged(config.copy(emergencyPhoneNumber = inputPhoneNumber.trim()))
                            focusManager.clearFocus()
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = activeAccent,
                        unfocusedBorderColor = colors.cardBorder,
                        focusedLabelColor = activeAccent,
                        cursorColor = activeAccent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // === SMS Tracking Frequency Slider ===
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.background)
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SMS TRACKING INTERVAL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textMuted,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Live GPS update frequency while alarm is active",
                                fontSize = 11.5.sp,
                                color = colors.textSecondary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = activeAccent.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, activeAccent.copy(alpha = 0.4f))
                        ) {
                            val intervalText = when {
                                config.smsIntervalSeconds < 60 -> "${config.smsIntervalSeconds} sec"
                                config.smsIntervalSeconds % 60 == 0 -> "${config.smsIntervalSeconds / 60} min"
                                else -> "${config.smsIntervalSeconds / 60}m ${config.smsIntervalSeconds % 60}s"
                            }
                            Text(
                                text = "Every $intervalText",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = activeAccent
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Slider(
                        value = config.smsIntervalSeconds.toFloat().coerceIn(30f, 600f),
                        onValueChange = { newVal ->
                            val snapped = (Math.round(newVal / 30f) * 30).coerceIn(30, 600)
                            onConfigChanged(config.copy(smsIntervalSeconds = snapped))
                        },
                        valueRange = 30f..600f,
                        steps = 18,
                        colors = SliderDefaults.colors(
                            thumbColor = activeAccent,
                            activeTrackColor = activeAccent,
                            inactiveTrackColor = colors.cardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("30s (Frequent)", fontSize = 10.5.sp, color = colors.textMuted)
                        Text("5m (Balanced)", fontSize = 10.5.sp, color = colors.textMuted)
                        Text("10m (Quota Saver)", fontSize = 10.5.sp, color = colors.textMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!hasSmsPermission) {
                    OutlinedButton(
                        onClick = { requestSmsPermission() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = activeAccent),
                        border = androidx.compose.foundation.BorderStroke(1.dp, activeAccent.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Outlined.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Grant SMS Permission", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = CyberGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SMS Permission Active & Verified", color = CyberGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
