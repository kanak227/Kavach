package com.kavach.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kavach.app.ui.theme.*

data class ManualSection(
    val icon: ImageVector,
    val title: String,
    val content: String,
    val tip: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManualScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalKavachColors.current
    val scrollState = rememberScrollState()
    val accentColor = if (colors.isDark) KavachGold else CyberCyan

    val sections = remember { getFullEnglishManualSections() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = "KAVACH USER MANUAL",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = OrbitronFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = colors.textPrimary
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colors.surface
            )
        )

        // Manual Scrollable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Header Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = accentColor.copy(alpha = 0.1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Complete Security Operations Guide",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Master all hardware alarms, Telegram surveillance & emergency dispatch controls",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CRITICAL SETUP & TESTING ADVISORY
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = KavachGold.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, KavachGold.copy(alpha = 0.85f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(KavachGold.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = null,
                                tint = KavachGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "PLEASE CALIBRATE & TEST AT HOME FIRST!",
                            fontFamily = OrbitronFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.5.sp,
                            letterSpacing = 0.8.sp,
                            color = KavachGold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Kavach is an aggressive, high-decibel anti-theft defense system. Because every phone's sensors, pocket fabric, desk surface, and travel cadence differ, calibrate sensitivity on the Dashboard and run a test arm/disarm session before public deployment:",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textPrimary,
                        lineHeight = 18.sp,
                        fontSize = 12.5.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "• Motion Sensitivity: Use the Home Dashboard slider (Level 1–10) to match your environment.\n" +
                                   "• Siren Volume & Sound: Pick from 10 distinct siren packs and test high-decibel output.\n" +
                                   "• Arming Countdown: Set a comfortable grace period (3s–30s) in Settings to place your phone down.\n" +
                                   "• Telegram SOS Bot: Configure your Bot Token and Chat ID to verify silent photo and GPS dispatch.\n" +
                                   "• Offline SMS Emergency Loop: Set your recovery phone number and interval (30s–10m) for zero-internet tracking.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            lineHeight = 17.sp,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }

            // All Sections
            sections.forEachIndexed { index, section ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = colors.card,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(accentColor.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = section.icon,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "${index + 1}. ${section.title}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = section.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            lineHeight = 20.sp,
                            fontSize = 13.sp
                        )

                        if (section.tip != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = CyberAmber.copy(alpha = 0.1f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CyberAmber.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Lightbulb,
                                        contentDescription = null,
                                        tint = CyberAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = section.tip,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

private fun getFullEnglishManualSections(): List<ManualSection> {
    return listOf(
        ManualSection(
            icon = Icons.Outlined.Shield,
            title = "Core Architecture & Privacy Guarantee",
            content = "Kavach is built as a 100% on-device, autonomous security shield. All accelerometer, optical proximity, and ambient light processing runs locally on your device hardware with zero cloud delay. Kavach has no developer servers, records no background telemetry, and stores nothing in the cloud. All emergency alerts and photos are transmitted directly and exclusively to your personal Telegram Bot and recovery SMS number.",
            tip = "Because Kavach runs 100% on-device, the motion alarm, flashlight strobe, and high-decibel sirens work completely offline without internet."
        ),
        ManualSection(
            icon = Icons.Outlined.TouchApp,
            title = "Arming the Shield & Grace Countdown",
            content = "To arm your phone, select a protection duration from the Timer Preset Row (1m, 5m, 15m, 30m, 1h, or Endless) and tap the central glowing Shield ring. An audible countdown will begin (default 5 seconds, adjustable from 3s to 30s in Settings). Use this grace period to place the phone down on a flat surface or slip it into your pocket. Once the countdown reaches zero, the shield enters armed state and takes an instant baseline stability calibration.",
            tip = "Keep the device still during the final second of countdown so it calibrates an accurate zero-gravity baseline."
        ),
        ManualSection(
            icon = Icons.Outlined.Vibration,
            title = "Desk Guard: Strict Vertical Lift Detection (Zero Slide False Alarms)",
            content = "When resting on a table, desk, or bedside nightstand, Kavach calculates the exact normal vector perpendicular to the surface. Any horizontal sliding across the table produces zero vertical force and is 100% ignored, so you will never be woken up by accidental hand brushes while sleeping! The alarm triggers strictly when the phone is picked up vertically off its baseline ground. Sensitivity ranges from Level 1 (heavy deliberate lift required, maximum sleep protection) to Level 10 (ultra feather-touch) directly on the Home Dashboard slider.",
            tip = "For overnight nightstand use, choose Level 1 or 2. You can slide the phone freely without triggering the alarm."
        ),
        ManualSection(
            icon = Icons.Outlined.LightMode,
            title = "Pocket Guard: 3-Sensor Voting Fusion & Anti-Walking Filter",
            content = "Pocket Guard combines three independent hardware sensors to prevent pickpocketing:\n" +
                      "1. Optical Proximity Sensor (detects pocket interior darkness & fabric proximity)\n" +
                      "2. Ambient Light Sensor (detects rapid light surges when phone is pulled out)\n" +
                      "3. Jerk/Tilt Accelerometer (detects vertical pocket withdrawal motion)\n\n" +
                      "Kavach features an intelligent walking filter that continuously distinguishes between normal rhythmic walking/jogging oscillations and sudden snatch extractions.",
            tip = "Calibrate the sensitivity slider to match your pants pocket depth and fabric thickness."
        ),
        ManualSection(
            icon = Icons.Outlined.Loop,
            title = "Pocket Guard Types: Standard vs Auto-Loop Commuter",
            content = "Kavach provides two operational types for Pocket Guard (both use the exact same sensor detection logic):\n\n" +
                      "• Type 1: Standard (Single-Arm): Arms once. Place phone in your pocket. Once extracted or triggered, you authenticate to disarm and the shield remains disarmed until manually armed again.\n\n" +
                      "• Type 2: Auto-Loop Commuter: Engineered specifically for bus, train, and metro commutes. When activated, place your phone in your pocket. Each time you take out your phone and authenticate, you can use your phone freely while the screen is on (guard stays dormant). As soon as you finish and turn off the screen (or press power button), Kavach automatically re-arms in your pocket using your arming countdown delay without needing to touch the app! When you arrive at your destination, tap 'Authenticate & Close Auto-Loop' inside the app to end the session permanently.",
            tip = "For daily commutes and public transport, use Type 2 Auto-Loop so you stay protected throughout your journey without repeatedly opening the app."
        ),
        ManualSection(
            icon = Icons.AutoMirrored.Outlined.VolumeUp,
            title = "High-Intensity Sirens & Hardware Volume Lock",
            content = "When an alarm detonates, Kavach immediately plays your chosen siren sound at maximum volume through the phone's primary loudspeaker. To defeat thieves who attempt to silence the phone, Kavach engages an aggressive hardware Volume Lock: if someone presses the volume-down button or attempts to mute the device, Kavach overrides the system audio manager and snaps the volume back to 100% every 300 milliseconds.\n\n" +
                      "Choose from 10 distinct high-intensity siren packs in the Siren Packs menu (Cyber Tactical, Police Interceptor, Air Raid Siren, Nuclear Alert, Biohazard Siren, Pulsar Sweep, Ultrasonic Deterrent, EMP Pulse, Red Alert, and Strobe Alarm).",
            tip = "The 300ms volume floor lock actively overrides physical volume rocker buttons to prevent quick silencing."
        ),
        ManualSection(
            icon = Icons.Outlined.FlashlightOn,
            title = "Visual Strobe Alarm (Emergency Flashlight)",
            content = "Along with the audible siren, Kavach pulses your phone's rear LED camera torch in a rapid, disorienting strobe pattern. This visually attracts immediate attention in dark environments and signals distress to everyone nearby. The flashlight strobe can be toggled on or off in the Settings menu according to your preference.",
            tip = "The strobe effect is highly effective in dark parking lots, movie theatres, hostels, and night streets."
        ),
        ManualSection(
            icon = Icons.Outlined.Fingerprint,
            title = "Disarming with Biometrics & PIN",
            content = "To silence and disarm the shield, authenticate with your biometric fingerprint, face unlock, or device lockscreen PIN/pattern. The alarm will never silence for an unauthorized person. If the device was triggered, successfully authenticating immediately deactivates the siren, stops the strobe, and notifies your emergency contact on Telegram that the device was safely disarmed.",
            tip = "If your fingerprint fails or your fingers are wet, tap 'Use Device PIN / Pattern' to disarm instantly."
        ),
        ManualSection(
            icon = Icons.Outlined.Schedule,
            title = "Stealth Decoy Clock Mode",
            content = "Enable 'Decoy Clock Mode' in Settings to disguise your armed phone as an innocent, minimalist bedside clock. When the countdown completes, the entire screen transitions into a stealth OLED black clock showing only the time. If an intruder touches the clock or moves the device, the stealth disguise vanishes and the full alarm detonates instantly.",
            tip = "Decoy Clock mode uses an ultra-low-power pure black background that consumes minimal battery on AMOLED screens."
        ),
        ManualSection(
            icon = Icons.AutoMirrored.Outlined.Send,
            title = "Telegram Emergency SOS & Continuous Live GPS Tracking",
            content = "When an alarm detonates, Kavach sends an instant SOS alert with an interactive Live Location map to your Telegram Bot. Kavach continuously refreshes the thief's live coordinates on the Telegram map every 20 seconds, allowing real-time pursuit without chat message spam! On-demand '/loc' remote buttons remain available as static map pins.",
            tip = "Live tracking operates continuously during breach alarms until you authenticate and disarm."
        ),
        ManualSection(
            icon = Icons.Outlined.PhotoCamera,
            title = "5-Photo Intruder Evidence Burst",
            content = "Upon alarm detonation, Kavach silently wakes the front camera and captures a rapid 5-photo burst sequence (1.2-second intervals). Using advanced hardware Auto-Exposure (3A) and universal lightness enhancement, Kavach brightens dark shots and delivers all 5 high-resolution face snapshots directly into your Telegram chat so you have clear visual evidence of the intruder.",
            tip = "Ensure Camera permission is granted in the Telegram Alert card so intruder photos can be taken."
        ),
        ManualSection(
            icon = Icons.Outlined.SettingsRemote,
            title = "Interactive 2-Way Telegram Remote Controls",
            content = "While the alarm is active, Kavach keeps a secure 2-way communication channel open with your Telegram Bot. You can send commands directly from Telegram:\n" +
                      "• /photo: Captures an immediate fresh front intruder photo\n" +
                      "• /backphoto: Captures an immediate fresh rear camera photo\n" +
                      "• /loc: Transmits fresh real-time GPS coordinates and Google Maps pin\n" +
                      "• /status: Reports battery percentage, siren state, and sensor diagnostics",
            tip = "Tap the quick-action keyboard buttons in Telegram to execute remote commands with a single tap."
        ),
        ManualSection(
            icon = Icons.Outlined.Sms,
            title = "Offline SMS Emergency Fallback & Connectivity Cut Detection",
            content = "If a thief quickly turns on Airplane Mode, cuts off Wi-Fi/Mobile Data, or if internet delivery fails, Kavach automatically detects the loss of connectivity and falls back to offline SMS dispatch. It immediately transmits an emergency SMS containing real-time Google Maps coordinates directly to your designated recovery phone number.",
            tip = "Configure your recovery phone number and grant SMS permission in the Telegram Alert card in Settings."
        ),
        ManualSection(
            icon = Icons.Outlined.ShareLocation,
            title = "Repeating Live GPS Tracking SMS Loop (Custom Interval 30s to 10m)",
            content = "When offline or when mobile data is severed, a single SMS is not enough if the thief is moving. Kavach includes a customizable Repeating Live GPS SMS Tracking Loop with intervals ranging from 30 seconds to 10 minutes (30s, 1m, 1m 30s, 2m, etc.). While the alarm remains breached, Kavach periodically reads fresh GPS satellites and dispatches updated Google Maps coordinates to your recovery number at your selected interval until you authenticate and disarm.",
            tip = "Adjust the GPS Tracking SMS Interval slider in Settings to balance tracking accuracy with SMS plan limits."
        ),
        ManualSection(
            icon = Icons.Outlined.Bedtime,
            title = "Night Guard Overnight Schedule",
            content = "Activate Night Guard to automatically guard your phone while you sleep. Set your sleep and wake hours (e.g. 11:00 PM to 07:00 AM). During this active window, Kavach presents an auto-arm reminder card on your dashboard, allowing you to arm your phone for the entire night with one tap.",
            tip = "Night Guard works seamlessly with the Decoy Clock for an ideal overnight bedside setup."
        ),
        ManualSection(
            icon = Icons.Outlined.PowerSettingsNew,
            title = "Hardware Anti-Shutdown Protection",
            content = "Thieves often attempt to immediately turn off stolen phones using physical power buttons. To defeat this, Kavach guides you to enable your phone's native 'Require Lock Screen PIN / Password to Power Off' feature in system Security & Privacy settings. With this enabled, the phone cannot be powered down or rebooted without your lock screen PIN/biometric, keeping Kavach active and continuously tracking.",
            tip = "Tap 'OPEN PHONE SETTINGS' on the Hardware Anti-Shutdown card in Settings to configure this system defense."
        )
    )
}
