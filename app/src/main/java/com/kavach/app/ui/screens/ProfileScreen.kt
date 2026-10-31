package com.kavach.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kavach.app.data.DetectionMode
import com.kavach.app.data.UserProfile
import com.kavach.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profile: UserProfile,
    onSaveProfile: (UserProfile) -> Unit,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalKavachColors.current
    val scrollState = rememberScrollState()

    var fullName by remember(profile.fullName) { mutableStateOf(profile.fullName) }
    var email by remember(profile.email) { mutableStateOf(profile.email) }
    var phone by remember(profile.phoneNumber) { mutableStateOf(profile.phoneNumber) }
    var emergencyName by remember(profile.emergencyContactName) { mutableStateOf(profile.emergencyContactName) }
    var emergencyPhone by remember(profile.emergencyContactPhone) { mutableStateOf(profile.emergencyContactPhone) }
    var bloodGroup by remember(profile.bloodGroup) { mutableStateOf(profile.bloodGroup) }
    var address by remember(profile.homeAddress) { mutableStateOf(profile.homeAddress) }
    var notes by remember(profile.notes) { mutableStateOf(profile.notes) }

    var isSavedSnackbarVisible by remember { mutableStateOf(false) }

    val activeAccent = if (colors.isDark) KavachGold else Color(0xFF00758F)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "USER PROFILE",
                fontFamily = OrbitronFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = colors.textPrimary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Profile Avatar Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.card)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(activeAccent.copy(alpha = 0.15f))
                        .border(1.5.dp, activeAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = activeAccent,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (fullName.isNotBlank()) fullName else "Device Owner",
                    fontFamily = OrbitronFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = colors.textPrimary
                )
                Text(
                    text = if (email.isNotBlank()) email else "Profile configured for backend sync",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section: Personal Details
        Text(
            text = "PERSONAL & EMERGENCY DETAILS",
            fontFamily = OrbitronFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = colors.textSecondary,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        ProfileTextField(
            label = "Full Name",
            value = fullName,
            onValueChange = { fullName = it },
            icon = Icons.Outlined.Badge,
            colors = colors
        )
        Spacer(modifier = Modifier.height(10.dp))

        ProfileTextField(
            label = "Email Address",
            value = email,
            onValueChange = { email = it },
            icon = Icons.Outlined.Email,
            keyboardType = KeyboardType.Email,
            colors = colors
        )
        Spacer(modifier = Modifier.height(10.dp))

        ProfileTextField(
            label = "Phone Number",
            value = phone,
            onValueChange = { phone = it },
            icon = Icons.Outlined.Phone,
            keyboardType = KeyboardType.Phone,
            colors = colors
        )
        Spacer(modifier = Modifier.height(10.dp))

        ProfileTextField(
            label = "Emergency Contact Person",
            value = emergencyName,
            onValueChange = { emergencyName = it },
            icon = Icons.Outlined.ContactEmergency,
            colors = colors
        )
        Spacer(modifier = Modifier.height(10.dp))

        ProfileTextField(
            label = "Emergency Contact Phone",
            value = emergencyPhone,
            onValueChange = { emergencyPhone = it },
            icon = Icons.Outlined.PhoneCallback,
            keyboardType = KeyboardType.Phone,
            colors = colors
        )
        Spacer(modifier = Modifier.height(10.dp))

        ProfileTextField(
            label = "Blood Group (Optional)",
            value = bloodGroup,
            onValueChange = { bloodGroup = it },
            icon = Icons.Outlined.LocalHospital,
            colors = colors
        )
        Spacer(modifier = Modifier.height(10.dp))

        ProfileTextField(
            label = "Home Address / Emergency Locality",
            value = address,
            onValueChange = { address = it },
            icon = Icons.Outlined.Home,
            colors = colors
        )
        Spacer(modifier = Modifier.height(10.dp))

        ProfileTextField(
            label = "Medical / Security Notes",
            value = notes,
            onValueChange = { notes = it },
            icon = Icons.Outlined.Notes,
            colors = colors
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Save Button
        Button(
            onClick = {
                onSaveProfile(
                    UserProfile(
                        fullName = fullName,
                        email = email,
                        phoneNumber = phone,
                        emergencyContactName = emergencyName,
                        emergencyContactPhone = emergencyPhone,
                        bloodGroup = bloodGroup,
                        homeAddress = address,
                        notes = notes
                    )
                )
                isSavedSnackbarVisible = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = activeAccent,
                contentColor = if (colors.isDark) Color.Black else Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SAVE PROFILE DETAILS",
                fontFamily = OrbitronFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        if (isSavedSnackbarVisible) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "✓ Profile details updated and queued for backend sync",
                color = CyberGreen,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun ProfileTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    colors: KavachColors
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.5.sp) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (colors.isDark) KavachGold else Color(0xFF00758F),
                modifier = Modifier.size(20.dp)
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (colors.isDark) KavachGold else Color(0xFF00758F),
            unfocusedBorderColor = colors.cardBorder,
            focusedLabelColor = if (colors.isDark) KavachGold else Color(0xFF00758F),
            unfocusedLabelColor = colors.textMuted,
            cursorColor = if (colors.isDark) KavachGold else Color(0xFF00758F),
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary
        ),
        singleLine = true
    )
}

@Composable
internal fun DailyGuardUsageGraph(
    hourlyUsage: Map<Int, Map<DetectionMode, Int>>,
    colors: KavachColors,
    isShieldArmed: Boolean = false
) {
    // Mode color mapping
    val travelColor = Color(0xFF06B6D4) // Cyan
    val sleepColor = Color(0xFFA855F7)  // Purple
    val deskColor = Color(0xFFF59E0B)   // Amber/Gold
    val pocketColor = Color(0xFF10B981) // Green

    val currentHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    // Default to 24h view if current hour is outside 9 AM - 9 PM so live activity is never cut off
    var view24h by remember { mutableStateOf(currentHour !in 9..21) }

    val hours = if (view24h) (0..23).toList() else (9..21).toList()
    val maxSeconds = 3600f // 1 hour = 3600s

    // Calculate total protection seconds today
    val totalSecondsToday = remember(hourlyUsage) {
        hourlyUsage.values.flatMap { it.values }.sum()
    }
    val totalMins = totalSecondsToday / 60
    val totalSecsRemainder = totalSecondsToday % 60
    val totalTimeText = if (totalMins > 0) "${totalMins}m ${totalSecsRemainder}s" else "${totalSecsRemainder}s"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = colors.card)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with Live Protection Status & View Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isShieldArmed) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(pocketColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LIVE RECORDING",
                                fontFamily = OrbitronFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = pocketColor
                            )
                        } else {
                            Text(
                                text = "TODAY'S ACTIVITY",
                                fontFamily = OrbitronFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = colors.textPrimary
                            )
                        }
                    }
                    Text(
                        text = "Total Protected: $totalTimeText",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Compact View Selector: 24H vs 9A-9P
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.background.copy(alpha = 0.6f))
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (view24h) (if (colors.isDark) KavachGold else CyberCyan).copy(alpha = 0.25f) else Color.Transparent)
                            .clickable { view24h = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "24H",
                            fontSize = 10.sp,
                            fontWeight = if (view24h) FontWeight.Bold else FontWeight.Normal,
                            color = if (view24h) (if (colors.isDark) KavachGold else CyberCyan) else colors.textMuted
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!view24h) (if (colors.isDark) KavachGold else CyberCyan).copy(alpha = 0.25f) else Color.Transparent)
                            .clickable { view24h = false }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "9A-9P",
                            fontSize = 10.sp,
                            fontWeight = if (!view24h) FontWeight.Bold else FontWeight.Normal,
                            color = if (!view24h) (if (colors.isDark) KavachGold else CyberCyan) else colors.textMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Mode Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LegendItem(label = "Travel", color = travelColor)
                LegendItem(label = "Sleep", color = sleepColor)
                LegendItem(label = "Desk", color = deskColor)
                LegendItem(label = "Pocket", color = pocketColor)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Chart Area with Y-Axis on left and Canvas on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Y-Axis Column (60m, 30m, 0m)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(32.dp)
                        .padding(bottom = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text("60m", fontSize = 9.sp, color = colors.textMuted, fontFamily = RajdhaniFamily, fontWeight = FontWeight.Bold)
                    Text("30m", fontSize = 9.sp, color = colors.textMuted, fontFamily = RajdhaniFamily, fontWeight = FontWeight.Bold)
                    Text("0m", fontSize = 9.sp, color = colors.textMuted, fontFamily = RajdhaniFamily, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Canvas Bar Chart
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    val w = size.width
                    val h = size.height - 4f // reserve 4px at bottom for crisp baseline
                    val slotWidth = w / hours.size

                    // Draw 60m top guide line
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.12f),
                        start = Offset(0f, 2f),
                        end = Offset(w, 2f),
                        strokeWidth = 1f
                    )

                    // Draw 30m mid guide line
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.15f),
                        start = Offset(0f, h / 2),
                        end = Offset(w, h / 2),
                        strokeWidth = 1f
                    )

                    // Draw 0m baseline axis
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.35f),
                        start = Offset(0f, h),
                        end = Offset(w, h),
                        strokeWidth = 1.5f
                    )

                    hours.forEachIndexed { index, hour ->
                        val xCenter = (index * slotWidth) + (slotWidth / 2f)
                        val barWidth = (slotWidth * 0.70f).coerceAtLeast(6f)
                        val xLeft = xCenter - (barWidth / 2f)

                        val usageForHour = hourlyUsage[hour] ?: emptyMap()
                        val travelSecs = usageForHour[DetectionMode.TRAVEL] ?: 0
                        val sleepSecs = usageForHour[DetectionMode.SLEEP] ?: 0
                        val deskSecs = usageForHour[DetectionMode.DESK] ?: 0
                        val pocketSecs = usageForHour[DetectionMode.POCKET] ?: 0

                        val totalSecs = (travelSecs + sleepSecs + deskSecs + pocketSecs).coerceAtMost(3600)

                        if (totalSecs > 0) {
                            var currentY = h

                            // Stacked bars: Pocket, Desk, Sleep, Travel
                            val segments = listOf(
                                pocketSecs to pocketColor,
                                deskSecs to deskColor,
                                sleepSecs to sleepColor,
                                travelSecs to travelColor
                            )

                            segments.forEach { (secs, color) ->
                                if (secs > 0) {
                                    val scaledHeight = (secs.toFloat() / maxSeconds) * h
                                    // Minimum 8px height so even 1-2 seconds of live testing are immediately visible
                                    val segmentHeight = scaledHeight.coerceAtLeast(8f)
                                    drawRect(
                                        color = color,
                                        topLeft = Offset(xLeft, (currentY - segmentHeight).coerceAtLeast(0f)),
                                        size = Size(barWidth, segmentHeight)
                                    )
                                    currentY -= segmentHeight
                                }
                            }

                            // If this hour is current hour and armed, draw a live pulse indicator above the bar
                            if (hour == currentHour && isShieldArmed) {
                                drawCircle(
                                    color = pocketColor,
                                    radius = 3.5f,
                                    center = Offset(xCenter, (currentY - 8f).coerceAtLeast(8f))
                                )
                            }
                        } else {
                            // Subtle empty dot at baseline
                            val isCurrentHour = (hour == currentHour)
                            drawCircle(
                                color = if (isCurrentHour && isShieldArmed) pocketColor else Color.Gray.copy(alpha = 0.25f),
                                radius = if (isCurrentHour && isShieldArmed) 4f else 2.5f,
                                center = Offset(xCenter, h - 3f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // X-Axis Hour Labels — strictly below the chart with left offset matching Y-axis
            if (view24h) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 38.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("12 AM", fontSize = 9.sp, color = colors.textMuted, fontFamily = RajdhaniFamily)
                    Text("6 AM", fontSize = 9.sp, color = colors.textMuted, fontFamily = RajdhaniFamily)
                    Text("12 PM", fontSize = 9.sp, color = colors.textMuted, fontFamily = RajdhaniFamily)
                    Text("6 PM", fontSize = 9.sp, color = colors.textMuted, fontFamily = RajdhaniFamily)
                    Text("11 PM", fontSize = 9.sp, color = colors.textMuted, fontFamily = RajdhaniFamily)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 38.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("9 AM", fontSize = 10.sp, color = colors.textMuted, fontFamily = RajdhaniFamily)
                    Text("12 PM", fontSize = 10.sp, color = colors.textMuted, fontFamily = RajdhaniFamily)
                    Text("3 PM", fontSize = 10.sp, color = colors.textMuted, fontFamily = RajdhaniFamily)
                    Text("6 PM", fontSize = 10.sp, color = colors.textMuted, fontFamily = RajdhaniFamily)
                    Text("9 PM", fontSize = 10.sp, color = colors.textMuted, fontFamily = RajdhaniFamily)
                }
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Gray
        )
    }
}

