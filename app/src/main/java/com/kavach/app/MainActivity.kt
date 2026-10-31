package com.kavach.app

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.navigation.compose.currentBackStackEntryAsState
import com.kavach.app.auth.BiometricAuthManager
import com.kavach.app.data.ShieldState
import com.kavach.app.service.KavachForegroundService
import com.kavach.app.ui.screens.DashboardScreen
import com.kavach.app.ui.screens.DecoyClockScreen
import com.kavach.app.ui.screens.SettingsScreen
import com.kavach.app.ui.screens.SirenPacksScreen
import com.kavach.app.ui.theme.*
import com.kavach.app.viewmodel.DashboardViewModel

class MainActivity : FragmentActivity() {

    private lateinit var biometricAuthManager: BiometricAuthManager
    private lateinit var dashboardViewModel: DashboardViewModel
    private val _hasNotificationPermission = androidx.compose.runtime.mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lock screen to vertical portrait mode strictly to preserve state and prevent data loss on rotation
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        // Cancel any lingering night auto-arm notification
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm?.cancelAll()
        enableEdgeToEdge()
        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        dashboardViewModel = androidx.lifecycle.ViewModelProvider(this)[DashboardViewModel::class.java]

        val isTriggeredOnLaunch = intent?.getBooleanExtra("SHIELD_TRIGGERED", false) == true ||
            KavachForegroundService.serviceShieldState.value == ShieldState.ALARM_ACTIVE ||
            KavachForegroundService.serviceShieldState.value == ShieldState.TRIGGERED

        if (isTriggeredOnLaunch) {
            updateLockScreenVisibility(true)
        } else {
            updateLockScreenVisibility(false)
        }

        // Check if relaunched after swipe-from-recents while armed
        handleRelaunchFromSwipe(intent)

        // Check if launched for night auto-arm
        handleNightAutoArm(intent)

        // Initialize biometric auth manager
        biometricAuthManager = BiometricAuthManager(this)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val state = dashboardViewModel.shieldState.value
                if (state == ShieldState.ARMED ||
                    state == ShieldState.TRIGGERED ||
                    state == ShieldState.ALARM_ACTIVE
                ) {
                    Toast.makeText(this@MainActivity, "Shield is armed. Authenticate to disarm.", Toast.LENGTH_SHORT).show()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        setContent {
            val viewModel: DashboardViewModel = dashboardViewModel

            val navController = rememberNavController()

            val shieldState by viewModel.shieldState.collectAsStateWithLifecycle()
            val remainingSeconds by viewModel.remainingSeconds.collectAsStateWithLifecycle()
            val armingCountdown by viewModel.armingCountdown.collectAsStateWithLifecycle()
            val decoyCountdown by viewModel.decoyCountdown.collectAsStateWithLifecycle()
            val sensorReading by viewModel.sensorReading.collectAsStateWithLifecycle()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val isTermsAccepted by viewModel.isTermsAccepted.collectAsStateWithLifecycle()
            val isNightGuardDismissed by viewModel.isNightGuardDismissedForTonight.collectAsStateWithLifecycle()
            val isNightWindowActive by viewModel.isNightWindowActive.collectAsStateWithLifecycle()
            val isAutoPocketActive by viewModel.isAutoPocketSessionActive.collectAsStateWithLifecycle()
            val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
            val hourlyUsage by viewModel.hourlyUsage.collectAsStateWithLifecycle()

            var showExitDialog by remember { mutableStateOf(false) }
            var isOpeningSplashFinished by rememberSaveable { mutableStateOf(false) }
            val hasNotificationPermission by _hasNotificationPermission

            // 1.2s opening splash animation (skipped immediately if alarm/triggered active)
            LaunchedEffect(Unit) {
                val isEmergency = shieldState == ShieldState.TRIGGERED ||
                    shieldState == ShieldState.ALARM_ACTIVE ||
                    KavachForegroundService.serviceShieldState.value == ShieldState.ALARM_ACTIVE ||
                    intent?.getBooleanExtra("SHIELD_TRIGGERED", false) == true

                if (isEmergency) {
                    isOpeningSplashFinished = true
                } else {
                    kotlinx.coroutines.delay(1200L)
                    isOpeningSplashFinished = true
                }
            }

            KavachTheme(isDarkMode = settings.isDarkMode) {
                val colors = LocalKavachColors.current

                // Manage lock screen visibility and biometric authentication
                LaunchedEffect(shieldState) {
                    val isAlarming = shieldState == ShieldState.TRIGGERED || shieldState == ShieldState.ALARM_ACTIVE
                    updateLockScreenVisibility(isAlarming)

                    when (shieldState) {
                        ShieldState.TRIGGERED, ShieldState.ALARM_ACTIVE -> {
                            isOpeningSplashFinished = true
                            // Auto-show biometric prompt when motion/light detected or alarm active
                            triggerBiometricAuth(
                                onSuccess = { viewModel.onAuthenticationSuccess() }
                            )
                        }
                        else -> { /* Keep current state */ }
                    }
                }

                // Close App Confirmation Dialog
                if (showExitDialog) {
                    AlertDialog(
                        onDismissRequest = { showExitDialog = false },
                        containerColor = colors.card,
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Shield,
                                    contentDescription = null,
                                    tint = if (colors.isDark) KavachGold else CyberCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Close Kavach?",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }
                        },
                        text = {
                            Text(
                                text = "Closing the app will pause instant UI monitoring. Are you sure you want to exit?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                                lineHeight = 20.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showExitDialog = false
                                    finish()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyberRed,
                                    contentColor = androidx.compose.ui.graphics.Color.White
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("EXIT APP", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExitDialog = false }) {
                                Text("STAY PROTECTED", color = if (colors.isDark) KavachGold else CyberCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.background)
                        .systemBarsPadding()
                ) {
                    if (!isOpeningSplashFinished && shieldState != ShieldState.TRIGGERED && shieldState != ShieldState.ALARM_ACTIVE) {
                        // Opening Icon Animation
                        val splashTransition = rememberInfiniteTransition(label = "splashPulse")
                        val splashScale by splashTransition.animateFloat(
                            initialValue = 0.94f,
                            targetValue = 1.06f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(750, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "splashScale"
                        )
                        val splashGlowAlpha by splashTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 0.85f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(750, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "splashGlow"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(DarkBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .scale(splashScale)
                                        .clip(CircleShape)
                                        .background(KavachGold.copy(alpha = splashGlowAlpha * 0.25f))
                                        .border(2.dp, KavachGold.copy(alpha = splashGlowAlpha), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.Image(
                                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.kavach_logo),
                                        contentDescription = "Kavach",
                                        modifier = Modifier.size(80.dp).clip(CircleShape)
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "KAVA",
                                        fontFamily = OrbitronFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 26.sp,
                                        letterSpacing = 4.sp,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "CH",
                                        fontFamily = OrbitronFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 26.sp,
                                        letterSpacing = 4.sp,
                                        color = KavachGold
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "STEALTH SHIELD",
                                    fontFamily = RajdhaniFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = 5.sp,
                                    color = KavachGold.copy(alpha = 0.85f)
                                )
                            }
                        }
                    } else if (!hasNotificationPermission) {
                        // Compulsory Notification Permission Screen
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(colors.background)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape)
                                        .background(com.kavach.app.ui.theme.KavachGold.copy(alpha = 0.15f))
                                        .border(2.dp, com.kavach.app.ui.theme.KavachGold, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Shield,
                                        contentDescription = null,
                                        tint = com.kavach.app.ui.theme.KavachGold,
                                        modifier = Modifier.size(50.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Text(
                                    text = "NOTIFICATION PERMISSION REQUIRED",
                                    fontFamily = OrbitronFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = TextPrimary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "Kavach requires notification permission to trigger background alarm sirens, display emergency disarm alerts, and keep continuous anti-theft protection active.\n\nWithout this permission, the app cannot protect your device.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 22.sp
                                )

                                Spacer(modifier = Modifier.height(32.dp))

                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            val shouldShowRationale = shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)
                                            val prefs = getSharedPreferences("kavach_cache_prefs", Context.MODE_PRIVATE)
                                            val askedBefore = prefs.getBoolean("notif_asked_before", false)

                                            if (!askedBefore || shouldShowRationale) {
                                                prefs.edit().putBoolean("notif_asked_before", true).apply()
                                                androidx.core.app.ActivityCompat.requestPermissions(
                                                    this@MainActivity,
                                                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                                                    101
                                                )
                                            } else {
                                                // System already locked permission dialog -> open app notification settings directly
                                                try {
                                                    val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                                                    }
                                                    startActivity(intent)
                                                } catch (_: Exception) {
                                                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                        data = android.net.Uri.fromParts("package", packageName, null)
                                                    }
                                                    startActivity(intent)
                                                }
                                            }
                                        } else {
                                            _hasNotificationPermission.value = true
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = com.kavach.app.ui.theme.KavachGold,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Text(
                                        text = "GRANT PERMISSION",
                                        fontFamily = OrbitronFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                TextButton(
                                    onClick = {
                                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = android.net.Uri.fromParts("package", packageName, null)
                                        }
                                        startActivity(intent)
                                    }
                                ) {
                                    Text(
                                        text = "Open App Settings",
                                        color = com.kavach.app.ui.theme.CyberCyan,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    } else if (!isTermsAccepted) {
                        // First-time Launch: Terms of Service & Permission Disclosures
                        com.kavach.app.ui.screens.TermsAndDisclosureScreen(
                            onAccept = { viewModel.acceptTerms() }
                        )
                    } else {
                        // Overlays based on state
                        when (shieldState) {
                            ShieldState.TRIGGERED -> {
                                // Decoy trap â€” fake ambient clock shown to thief
                                DecoyClockScreen(
                                    onScreenTapped = {
                                        triggerBiometricAuth(
                                            onSuccess = { viewModel.onAuthenticationSuccess() }
                                        )
                                    }
                                )
                            }
                            ShieldState.ALARM_ACTIVE -> {
                                // Full siren blaring â€” high-contrast emergency disarm screen
                                com.kavach.app.ui.screens.AlarmActiveScreen(
                                    onDisarmAttempt = {
                                        triggerBiometricAuth(
                                            onSuccess = { viewModel.onAuthenticationSuccess() }
                                        )
                                    }
                                )
                            }
                            else -> {
                                // Normal navigation with Bottom Bar
                                val navBackStackEntry by navController.currentBackStackEntryAsState()
                                val currentRoute = navBackStackEntry?.destination?.route ?: "dashboard"
                                val showBottomBar = currentRoute in listOf("dashboard", "profile", "settings")

                                Scaffold(
                                    containerColor = colors.background,
                                    bottomBar = {
                                        if (showBottomBar) {
                                            NavigationBar(
                                                containerColor = colors.card,
                                                contentColor = colors.textPrimary,
                                                tonalElevation = 8.dp
                                            ) {
                                                val navItems = listOf(
                                                    Triple("dashboard", "Home", Icons.Outlined.Home to Icons.Filled.Home),
                                                    Triple("profile", "Profile", Icons.Outlined.Person to Icons.Filled.Person),
                                                    Triple("settings", "Settings", Icons.Outlined.Settings to Icons.Filled.Settings)
                                                )

                                                navItems.forEach { (route, label, icons) ->
                                                    val isSelected = currentRoute == route
                                                    NavigationBarItem(
                                                        selected = isSelected,
                                                        onClick = {
                                                            if (currentRoute != route) {
                                                                if (route == "profile") {
                                                                    viewModel.refreshUsageStats()
                                                                }
                                                                navController.navigate(route) {
                                                                    popUpTo("dashboard") {
                                                                        saveState = true
                                                                    }
                                                                    launchSingleTop = true
                                                                    restoreState = true
                                                                }
                                                            }
                                                        },
                                                        icon = {
                                                            Icon(
                                                                imageVector = if (isSelected) icons.second else icons.first,
                                                                contentDescription = label
                                                            )
                                                        },
                                                        label = {
                                                            Text(
                                                                text = label,
                                                                fontFamily = RajdhaniFamily,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                                fontSize = 12.sp
                                                            )
                                                        },
                                                        colors = NavigationBarItemDefaults.colors(
                                                            selectedIconColor = if (colors.isDark) KavachGold else CyberCyan,
                                                            selectedTextColor = if (colors.isDark) KavachGold else CyberCyan,
                                                            unselectedIconColor = colors.textSecondary.copy(alpha = 0.6f),
                                                            unselectedTextColor = colors.textSecondary.copy(alpha = 0.6f),
                                                            indicatorColor = (if (colors.isDark) KavachGold else CyberCyan).copy(alpha = 0.15f)
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                ) { innerPadding ->
                                    NavHost(
                                        navController = navController,
                                        startDestination = "dashboard",
                                        modifier = Modifier.padding(innerPadding)
                                    ) {
                                        composable("dashboard") {
                                            // Intercept back button on dashboard to show Close warning dialog
                                            androidx.activity.compose.BackHandler(enabled = shieldState == ShieldState.DISARMED) {
                                                showExitDialog = true
                                            }

                                            // Auto-activate night schedule immediately on dashboard if time is in range
                                            LaunchedEffect(settings.nightSchedule) {
                                                viewModel.checkAndTriggerNightScheduleNow()
                                            }

                                            Box(modifier = Modifier.fillMaxSize()) {
                                                DashboardScreen(
                                                    shieldState = shieldState,
                                                    remainingSeconds = remainingSeconds,
                                                    armingCountdown = armingCountdown,
                                                    decoyCountdown = decoyCountdown,
                                                    sensorReading = sensorReading,
                                                    settings = settings,
                                                    hourlyUsage = hourlyUsage,
                                                    isNightGuardDismissedForTonight = isNightGuardDismissed,
                                                    isNightWindowActive = isNightWindowActive,
                                                    onArmShield = { viewModel.armShield() },
                                                    onDisarmShield = {
                                                        if (shieldState == ShieldState.ARMED || shieldState == ShieldState.TRIGGERED || shieldState == ShieldState.ALARM_ACTIVE) {
                                                            triggerBiometricAuth(
                                                                onSuccess = { viewModel.onAuthenticationSuccess() }
                                                            )
                                                        } else {
                                                            viewModel.disarmShield()
                                                        }
                                                    },
                                                    onCancelNightGuardForTonight = {
                                                        viewModel.cancelNightGuardForTonight()
                                                    },
                                                    onResumeNightGuard = {
                                                        viewModel.resumeNightGuard()
                                                    },
                                                    onTimerPresetSelected = { viewModel.selectTimerPreset(it) },
                                                    onSensitivityChanged = { viewModel.updateSensitivity(it) },
                                                    onSettingsChanged = { viewModel.updateSettings(it) },
                                                    isAutoPocketSessionActive = isAutoPocketActive,
                                                    onPauseTransitSession = { viewModel.pauseTransitSession() },
                                                    onResumeTransitSession = { viewModel.resumeTransitSession() },
                                                    onQuitAutoLoop = {
                                                        triggerBiometricAuth(
                                                            force = true,
                                                            onSuccess = { viewModel.quitAutoLoopSession() }
                                                        )
                                                    },
                                                    onNavigateToSettings = {
                                                        navController.navigate("settings")
                                                    },
                                                    onNavigateToProfile = {
                                                        viewModel.refreshUsageStats()
                                                        navController.navigate("profile")
                                                    }
                                                )
                                            }
                                        }

                                        composable("profile") {
                                            com.kavach.app.ui.screens.ProfileScreen(
                                                profile = userProfile,
                                                settings = settings,
                                                onSaveProfile = { updated ->
                                                    viewModel.saveUserProfile(updated)
                                                },
                                                onSettingsChanged = { updatedSettings ->
                                                    viewModel.updateSettings(updatedSettings)
                                                },
                                                onNavigateBack = { navController.popBackStack() }
                                            )
                                        }

                                        composable("settings") {
                                            SettingsScreen(
                                                settings = settings,
                                                onSettingsChanged = { viewModel.updateSettings(it) },
                                                onTestAlarm = { viewModel.testAlarm() },
                                                onNavigateBack = { navController.popBackStack() },
                                                onNavigateToSirenPacks = {
                                                    navController.navigate("siren_packs")
                                                },
                                                onNavigateToUserManual = {
                                                    navController.navigate("user_manual")
                                                }
                                            )
                                        }

                                        composable("siren_packs") {
                                            SirenPacksScreen(
                                                selectedPackId = settings.selectedSirenPackId,
                                                unlockedPacks = settings.unlockedSirenPacks,
                                                onPackSelected = { packId ->
                                                    viewModel.updateSettings(
                                                        settings.copy(selectedSirenPackId = packId)
                                                    )
                                                },
                                                onWatchAdToUnlock = { pack ->
                                                    viewModel.unlockSirenPack(pack.id)
                                                    viewModel.updateSettings(
                                                        settings.copy(selectedSirenPackId = pack.id)
                                                    )
                                                },
                                                onPreviewSound = { packId ->
                                                    viewModel.previewSiren(packId)
                                                },
                                                onStopPreview = {
                                                    viewModel.stopPreview()
                                                },
                                                onNavigateBack = {
                                                    viewModel.stopPreview()
                                                    navController.popBackStack()
                                                }
                                            )
                                        }

                                        composable("user_manual") {
                                            com.kavach.app.ui.screens.UserManualScreen(
                                                onNavigateBack = { navController.popBackStack() }
                                            )
                                        }
                                    }
                                }
                            }
                    }
                }
            }
        }
    }
}

    /**
     * Intercept volume key presses when shield is armed.
     * Prevents thief from lowering volume to silence the alarm,
     * and blocks Power + Volume combinations used by OnePlus, Pixel, etc.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val state = dashboardViewModel.shieldState.value
        if (state == ShieldState.ALARM_ACTIVE || state == ShieldState.TRIGGERED) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
                return true // Block mute button completely while alarm is sounding
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                if (audioManager != null) {
                    val minFloor = dashboardViewModel.alarmController.getMinVolumeFloor(android.media.AudioManager.STREAM_ALARM)
                    val curVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
                    if (curVol <= minFloor) {
                        return true // Block lowering volume below configured floor
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Handle relaunch when the foreground service brings the app back
     * after the thief tried to swipe it from recents.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRelaunchFromSwipe(intent)
        checkAndTriggerAuth()
    }

    private fun handleRelaunchFromSwipe(intent: Intent?) {
        if (intent?.getBooleanExtra("RELAUNCH_FROM_SWIPE", false) == true) {
            Toast.makeText(
                this,
                "\u26A0\uFE0F Shield is armed! Authenticate to dismiss.",
                Toast.LENGTH_LONG
            ).show()
            // Trigger biometric auth so the real owner can dismiss if needed
            triggerBiometricAuth(
                onSuccess = { dashboardViewModel.onAuthenticationSuccess() }
            )
        }
    }

    /**
     * Handle night auto-arm intent from NightScheduleReceiver.
     * Automatically arms the shield with the configured night duration.
     */
    private fun handleNightAutoArm(intent: Intent?) {
        if (intent?.getBooleanExtra("NIGHT_AUTO_ARM", false) == true) {
            if (dashboardViewModel.shieldState.value == com.kavach.app.data.ShieldState.DISARMED) {
                val schedule = dashboardViewModel.settings.value.nightSchedule
                val startMinutes = schedule.startHour * 60 + schedule.startMinute
                val endMinutes = schedule.endHour * 60 + schedule.endMinute
                val durationMinutes = if (endMinutes > startMinutes) {
                    endMinutes - startMinutes
                } else {
                    (24 * 60 - startMinutes) + endMinutes
                }
                dashboardViewModel.armShieldWithDuration(durationMinutes.coerceAtLeast(30))
                Toast.makeText(
                    this,
                    "🌙 Night Shield activated automatically!",
                    Toast.LENGTH_LONG
                ).show()
            }
            intent.removeExtra("NIGHT_AUTO_ARM")
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return super.onKeyUp(keyCode, event)
    }

    private fun triggerBiometricAuth(force: Boolean = false, onSuccess: () -> Unit) {
        val state = dashboardViewModel.shieldState.value
        if (!force && (state == ShieldState.DISARMED || state == ShieldState.AUTHENTICATED)) {
            intent?.removeExtra("SHIELD_TRIGGERED")
            return
        }

        if (biometricAuthManager.isAnyAuthAvailable()) {
            biometricAuthManager.authenticate(
                onSuccess = {
                    intent?.removeExtra("SHIELD_TRIGGERED")
                    onSuccess()
                },
                onFailure = { /* Auth failed â€” countdown continues */ },
                onError = { _, _ -> /* Error â€” countdown continues */ }
            )
        } else {
            // No device credential configured â€” disarm immediately
            intent?.removeExtra("SHIELD_TRIGGERED")
            onSuccess()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        checkAndTriggerAuth()
    }

    override fun onStart() {
        super.onStart()
        com.kavach.app.service.KavachForegroundService.isAppInForeground = true
        // If user tapped to redirect into the app, dismiss the notification
        val isTriggeredIntent = intent?.getBooleanExtra("SHIELD_TRIGGERED", false) == true
        if (isTriggeredIntent) {
            try {
                val nm = getSystemService(android.app.NotificationManager::class.java)
                nm.cancel(com.kavach.app.service.KavachForegroundService.NOTIFICATION_ID)
                nm.cancel(com.kavach.app.service.KavachForegroundService.ALARM_NOTIFICATION_ID)
            } catch (_: Exception) {}
        }
    }

    override fun onStop() {
        super.onStop()
        com.kavach.app.service.KavachForegroundService.isAppInForeground = false
    }

    override fun onResume() {
        super.onResume()
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            _hasNotificationPermission.value = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            _hasNotificationPermission.value = true
        }
        checkAndTriggerAuth()
    }

    private fun updateLockScreenVisibility(isAlarming: Boolean) {
        val serviceState = KavachForegroundService.serviceShieldState.value
        val isServiceAlarming = serviceState == ShieldState.TRIGGERED || serviceState == ShieldState.ALARM_ACTIVE
        val shouldShow = isAlarming || isServiceAlarming

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(shouldShow)
            setTurnScreenOn(shouldShow)
        } else {
            @Suppress("DEPRECATION")
            if (shouldShow) {
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            } else {
                window.clearFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
        }
        if (shouldShow) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun checkAndTriggerAuth() {
        val state = dashboardViewModel.shieldState.value
        val serviceState = KavachForegroundService.serviceShieldState.value

        // CRITICAL: If disarmed or already authenticated, NEVER trigger auth!
        if (state == ShieldState.DISARMED || state == ShieldState.AUTHENTICATED ||
            serviceState == ShieldState.DISARMED) {
            intent?.removeExtra("SHIELD_TRIGGERED")
            return
        }

        val isTriggeredIntent = intent?.getBooleanExtra("SHIELD_TRIGGERED", false) == true ||
            intent?.getBooleanExtra("DISMISS_POWER_MENU", false) == true

        if (state == ShieldState.TRIGGERED || state == ShieldState.ALARM_ACTIVE ||
            serviceState == ShieldState.TRIGGERED || serviceState == ShieldState.ALARM_ACTIVE ||
            isTriggeredIntent) {
            intent?.removeExtra("SHIELD_TRIGGERED")
            updateLockScreenVisibility(true)
            triggerBiometricAuth(onSuccess = { dashboardViewModel.onAuthenticationSuccess() })
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
            _hasNotificationPermission.value = granted
        } else if (requestCode == 104) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) {
                Toast.makeText(this, "Emergency SMS permission granted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == BiometricAuthManager.REQUEST_CODE_DEVICE_CREDENTIAL) {
            if (resultCode == Activity.RESULT_OK) {
                biometricAuthManager.onCredentialSuccess()
                dashboardViewModel.onAuthenticationSuccess()
            } else {
                biometricAuthManager.cancelAuthentication()
            }
        }
    }
}


