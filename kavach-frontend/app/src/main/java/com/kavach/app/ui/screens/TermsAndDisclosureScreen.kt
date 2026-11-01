package com.kavach.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kavach.app.R
import com.kavach.app.ui.theme.*

/**
 * Clean, luxury first-time Terms & Conditions and Service Permissions Disclosure.
 * Explains exactly what hardware sensors and services Kavach uses and why.
 * Once accepted, saved permanently until the app is uninstalled.
 */
@Composable
fun TermsAndDisclosureScreen(
    onAccept: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var hasScrolledToBottom by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Track scroll position to ensure user reviews terms
    LaunchedEffect(scrollState.value, scrollState.maxValue) {
        if (scrollState.maxValue == 0 || scrollState.value >= scrollState.maxValue - 50) {
            hasScrolledToBottom = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Logo & Title
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(KavachGold.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.kavach_logo_header),
                    contentDescription = "Kavach",
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "KAVA",
                    fontFamily = OrbitronFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    letterSpacing = 3.sp,
                    color = TextPrimary
                )
                Text(
                    text = "CH",
                    fontFamily = OrbitronFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    letterSpacing = 3.sp,
                    color = KavachGold
                )
            }

            Text(
                text = "TERMS OF SERVICE & PERMISSIONS",
                fontFamily = RajdhaniFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 4.sp,
                color = KavachGold.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Welcome to Kavach. To protect your phone against theft, pickpocketing, and unauthorized tampering, Kavach requires permission to access specific on-device sensors and security features.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // CRITICAL SETUP & TESTING NOTICE FOR NEW USERS (BIG LETTERS)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = KavachGold.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    KavachGold.copy(alpha = 0.9f)
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(KavachGold.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = null,
                                tint = KavachGold,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "ATTENTION ALL NEW USERS",
                                fontFamily = OrbitronFamily,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                letterSpacing = 2.sp,
                                color = KavachGold
                            )
                            Text(
                                text = "CALIBRATION & TESTING REQUIRED",
                                fontFamily = RajdhaniFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 1.sp,
                                color = TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "PLEASE ADJUST SETTINGS & TEST THE APP ACCORDING TO YOUR INDIVIDUAL NEEDS FIRST!",
                        fontFamily = OrbitronFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        letterSpacing = 0.5.sp,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Kavach is engineered for diverse users, devices, and real-world situations. Because individual pocket tightness, clothing fabric, bag compartments, walking styles, and sound tolerances vary greatly, please configure settings that favor your personal conditions:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary.copy(alpha = 0.95f),
                        lineHeight = 20.sp,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Text(text = "• ", color = KavachGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = "Desk & Pocket Sensitivity: Tune sensitivity (Level 1 to 10) in Settings. Desk Guard uses vertical lift detection to ignore sleeping table slides, while Pickpocket Guard tunes lux and extraction jerk thresholds.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                lineHeight = 18.sp,
                                fontSize = 12.sp
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Text(text = "• ", color = KavachGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = "Alarm Volume & Tone: Set your alarm volume floor (e.g. 15% to 100%) and select your preferred siren tone in Siren Packs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                lineHeight = 18.sp,
                                fontSize = 12.sp
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Text(text = "• ", color = KavachGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = "Countdowns & Delays: Adjust the arming countdown (3s-30s) and trigger grace delay to give yourself comfortable time to put away or unlock your phone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                lineHeight = 18.sp,
                                fontSize = 12.sp
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Text(text = "• ", color = KavachGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = "Alarm Duration: Choose Endless alarm or auto-silence timer (1 to 60 minutes) to suit your personal preference.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                lineHeight = 18.sp,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = KavachGold.copy(alpha = 0.18f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, KavachGold.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = KavachGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ALWAYS TEST ARMING & DISARMING AT HOME BEFORE FIRST USE!",
                                fontFamily = OrbitronFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp,
                                color = KavachGold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Services & Permissions Disclosure Cards
            ServiceDisclosureCard(
                icon = Icons.Outlined.Vibration,
                title = "Accelerometer (Motion Sensor)",
                purpose = "Detects when the phone is picked up, bumped, or moved from a resting position while armed."
            )

            Spacer(modifier = Modifier.height(12.dp))

            ServiceDisclosureCard(
                icon = Icons.Outlined.LightMode,
                title = "Ambient Light & Proximity Sensors",
                purpose = "Detects when the phone is taken out of a pocket or bag in transit (Metro/Bus) and suppresses false alarms while walking."
            )

            Spacer(modifier = Modifier.height(12.dp))

            ServiceDisclosureCard(
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                title = "Audio & Volume Control",
                purpose = "Plays emergency sirens and locks volume during an active alarm to prevent a thief from silencing the phone."
            )

            Spacer(modifier = Modifier.height(12.dp))

            ServiceDisclosureCard(
                icon = Icons.Outlined.FlashlightOn,
                title = "Camera Torch (Flashlight)",
                purpose = "Pulses the phone's LED flash during an intruder alarm for visual distress signaling (Optional: can be disabled in Settings for photosensitive comfort)."
            )

            Spacer(modifier = Modifier.height(12.dp))

            ServiceDisclosureCard(
                icon = Icons.Outlined.GpsFixed,
                title = "Location Services (GPS Emergency Dispatch)",
                purpose = "Provides precise latitude & longitude coordinates to your designated Telegram emergency contact when an alarm detonates, assisting in recovery."
            )

            Spacer(modifier = Modifier.height(12.dp))

            ServiceDisclosureCard(
                icon = Icons.Outlined.PhotoCamera,
                title = "Camera (Intruder Evidence Capture)",
                purpose = "Silently captures front-camera evidence snapshots of unauthorized persons attempting to handle or steal your armed device."
            )

            Spacer(modifier = Modifier.height(12.dp))

            ServiceDisclosureCard(
                icon = Icons.Outlined.Sms,
                title = "Emergency SMS Fallback (Offline Recovery)",
                purpose = "If mobile data is disabled by an intruder or internet delivery fails, dispatches an offline SMS with live Google Maps coordinates to your designated emergency contact."
            )

            Spacer(modifier = Modifier.height(12.dp))

            Spacer(modifier = Modifier.height(16.dp))

            // AUTONOMOUS SECURITY & TAMPER GUARD CARD
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = KavachGold.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    KavachGold.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(KavachGold.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = KavachGold,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Autonomous Tamper Guard",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Continuous Background Protection",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = KavachGold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Kavach operates as a persistent security service to guard your device continuously, even when the screen is locked or the app is closed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 18.sp,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = KavachGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Volume Floor Lock: Prevents thieves from muting the siren.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = KavachGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reboot Auto-Shield: Shield re-arms automatically on restart.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = KavachGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Biometric Disarm: Instantly disarm using your fingerprint or PIN.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Strict Privacy & Permission Guarantee Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkCard)
                    .border(1.dp, KavachGold.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(18.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = KavachGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PRIVACY & PERMISSION GUARANTEE",
                            fontFamily = OrbitronFamily,
                            fontSize = 13.sp,
                            color = KavachGold,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "• NO Camera Access Without Explicit Permission: The camera hardware is strictly dormant during normal use. Kavach NEVER accesses or records through the camera unless an armed alarm actively detonates, and ONLY if you have explicitly granted Camera permission and enabled intruder capture in Settings.\n\n" +
                               "• NO Background Location Tracking: Kavach does NOT track your daily movements, build location histories, or log GPS in the background. GPS coordinates are queried exclusively at the exact moment of an alarm detonation to generate an emergency recovery pin, and ONLY if Location permission is granted and device GPS is turned on.\n\n" +
                               "• 100% Zero-Server & Zero-Cloud Architecture: Kavach does not operate developer servers or analytics databases. All emergency photos and GPS coordinates are transmitted directly from your phone into your personal, private Telegram Bot. We never see, store, or sell your data.\n\n" +
                               "• Full User Control: You can revoke Camera or Location permissions at any time directly through your phone's Android App Settings without disabling the core motion and siren alarm.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 19.sp,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Accept & Continue Button
            Button(
                onClick = onAccept,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KavachGold,
                    contentColor = DarkBackground
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AGREE & START KAVACH",
                    fontFamily = RajdhaniFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ServiceDisclosureCard(
    icon: ImageVector,
    title: String,
    purpose: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkCard)
            .border(1.dp, DarkCardBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(KavachGoldContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = KavachGold,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = purpose,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                lineHeight = 16.sp,
                fontSize = 12.sp
            )
        }
    }
}


