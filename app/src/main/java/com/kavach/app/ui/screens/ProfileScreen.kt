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
    colors: KavachColors
) {
    // Mode color mapping
    val travelColor = Color(0xFF06B6D4) // Cyan
    val sleepColor = Color(0xFFA855F7)  // Purple
    val deskColor = Color(0xFFF59E0B)   // Amber/Gold
    val pocketColor = Color(0xFF10B981) // Green

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = colors.card)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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

            Spacer(modifier = Modifier.height(16.dp))

            // Canvas Bar Chart for hours 9 AM (9) to 9 PM (21)
            val hours = (9..21).toList()
            val maxMinutes = 60f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height - 30f // reserve space for hour labels
                    val slotWidth = w / hours.size

                    // Draw baseline axis
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.3f),
                        start = Offset(0f, h),
                        end = Offset(w, h),
                        strokeWidth = 1f
                    )

                    // Draw grid reference lines at 30m and 60m
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.15f),
                        start = Offset(0f, h / 2),
                        end = Offset(w, h / 2),
                        strokeWidth = 1f
                    )

                    hours.forEachIndexed { index, hour ->
                        val xCenter = (index * slotWidth) + (slotWidth / 2f)
                        val barWidth = (slotWidth * 0.65f).coerceAtLeast(8f)
                        val xLeft = xCenter - (barWidth / 2f)

                        val usageForHour = hourlyUsage[hour] ?: emptyMap()
                        val travelMins = usageForHour[DetectionMode.TRAVEL] ?: 0
                        val sleepMins = usageForHour[DetectionMode.SLEEP] ?: 0
                        val deskMins = usageForHour[DetectionMode.DESK] ?: 0
                        val pocketMins = usageForHour[DetectionMode.POCKET] ?: 0

                        val totalMins = (travelMins + sleepMins + deskMins + pocketMins).coerceAtMost(60)

                        if (totalMins > 0) {
                            var currentY = h

                            // Stacked bars
                            val segments = listOf(
                                pocketMins to pocketColor,
                                deskMins to deskColor,
                                sleepMins to sleepColor,
                                travelMins to travelColor
                            )

                            segments.forEach { (mins, color) ->
                                if (mins > 0) {
                                    val segmentHeight = (mins / maxMinutes) * h
                                    drawRect(
                                        color = color,
                                        topLeft = Offset(xLeft, currentY - segmentHeight),
                                        size = Size(barWidth, segmentHeight)
                                    )
                                    currentY -= segmentHeight
                                }
                            }
                        } else {
                            // Subtle empty dot at baseline
                            drawCircle(
                                color = Color.Gray.copy(alpha = 0.25f),
                                radius = 2.5f,
                                center = Offset(xCenter, h - 3f)
                            )
                        }
                    }
                }

                // X-Axis Hour Labels (9 AM, 12 PM, 3 PM, 6 PM, 9 PM)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("9 AM", fontSize = 10.sp, color = colors.textMuted)
                    Text("12 PM", fontSize = 10.sp, color = colors.textMuted)
                    Text("3 PM", fontSize = 10.sp, color = colors.textMuted)
                    Text("6 PM", fontSize = 10.sp, color = colors.textMuted)
                    Text("9 PM", fontSize = 10.sp, color = colors.textMuted)
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

