package com.kavach.app.ui.screens

import android.app.TimePickerDialog
import java.util.Calendar
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.kavach.app.data.DetectionMode
import com.kavach.app.data.KavachSettings
import com.kavach.app.data.SosContact
import com.kavach.app.data.UserProfile
import com.kavach.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profile: UserProfile,
    settings: KavachSettings = KavachSettings(),
    onSaveProfile: (UserProfile) -> Unit,
    onSettingsChanged: (KavachSettings) -> Unit = {},
    onSignOut: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalKavachColors.current
    val scrollState = rememberScrollState()

    var fullName by remember(profile.fullName) { mutableStateOf(profile.fullName) }
    var phone by remember(profile.phoneNumber) { mutableStateOf(profile.phoneNumber) }
    var profilePicturePath by remember(profile.profilePicturePath) { mutableStateOf(profile.profilePicturePath) }

    // SOS Contacts State
    var sosContacts by remember(profile.sosContacts) {
        val initialList = if (profile.sosContacts.isNotEmpty()) {
            profile.sosContacts
        } else if (profile.emergencyContactName.isNotBlank() || profile.emergencyContactPhone.isNotBlank()) {
            listOf(SosContact(name = profile.emergencyContactName, phone = profile.emergencyContactPhone))
        } else {
            listOf(SosContact(name = "", phone = ""))
        }
        mutableStateOf(initialList)
    }

    // Sleep Schedule State
    var sleepScheduleSelection by remember(profile.sleepScheduleAnswer) {
        val defaultAns = if (profile.sleepScheduleAnswer.isNotBlank()) {
            profile.sleepScheduleAnswer
        } else if (settings.nightSchedule.enabled) {
            "Yes"
        } else {
            ""
        }
        mutableStateOf(defaultAns)
    }
    var sleepStartTime by remember(profile.sleepScheduleStart) {
        mutableStateOf(if (profile.sleepScheduleStart.isNotBlank()) profile.sleepScheduleStart else "11:00 PM")
    }
    var sleepEndTime by remember(profile.sleepScheduleEnd) {
        mutableStateOf(if (profile.sleepScheduleEnd.isNotBlank()) profile.sleepScheduleEnd else "07:00 AM")
    }

    var isSavedSnackbarVisible by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showPhotoOptionsDialog by remember { mutableStateOf(false) }
    var showZoomOverlay by remember { mutableStateOf(false) }
    var avatarBitmapKey by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val context = LocalContext.current
    val avatarFile = remember { java.io.File(context.filesDir, "profile_avatar.jpg") }
    val tempCameraFile = remember { java.io.File(context.cacheDir, "temp_camera_photo.jpg") }

    val avatarBitmap = remember(profilePicturePath, avatarBitmapKey) {
        if (profilePicturePath.isNotBlank()) {
            try {
                val f = java.io.File(profilePicturePath)
                if (f.exists()) {
                    BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
                } else null
            } catch (_: Exception) { null }
        } else null
    }

    fun saveAvatarFromStream(inputStream: java.io.InputStream) {
        try {
            val bytes = inputStream.readBytes()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sampleSize = 1
            val maxDimension = 1080
            while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            if (bitmap != null) {
                avatarFile.parentFile?.mkdirs()
                avatarFile.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                profilePicturePath = avatarFile.absolutePath
                avatarBitmapKey = System.currentTimeMillis()
                val primaryName = sosContacts.firstOrNull()?.name ?: ""
                val primaryPhone = sosContacts.firstOrNull()?.phone ?: ""
                onSaveProfile(
                    profile.copy(
                        fullName = fullName,
                        phoneNumber = phone,
                        emergencyContactName = primaryName,
                        emergencyContactPhone = primaryPhone,
                        sosContacts = sosContacts,
                        sleepScheduleAnswer = sleepScheduleSelection,
                        sleepScheduleStart = sleepStartTime,
                        sleepScheduleEnd = sleepEndTime,
                        profilePicturePath = avatarFile.absolutePath
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("ProfileScreen", "Failed to process and save avatar", e)
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraFile.exists()) {
            try {
                tempCameraFile.inputStream().use { stream ->
                    saveAvatarFromStream(stream)
                }
                tempCameraFile.delete()
            } catch (e: Exception) {
                Log.e("ProfileScreen", "Failed to load captured photo", e)
            }
        }
    }

    val pickGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    saveAvatarFromStream(input)
                }
            } catch (e: Exception) {
                Log.e("ProfileScreen", "Failed to open gallery image stream", e)
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                tempCameraFile.parentFile?.mkdirs()
                if (!tempCameraFile.exists()) {
                    tempCameraFile.createNewFile()
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tempCameraFile
                )
                takePictureLauncher.launch(uri)
            } catch (e: Exception) {
                Log.e("ProfileScreen", "Failed to launch camera after permission", e)
            }
        }
    }

    fun launchCamera() {
        val hasCam = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasCam) {
            try {
                tempCameraFile.parentFile?.mkdirs()
                if (!tempCameraFile.exists()) {
                    tempCameraFile.createNewFile()
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tempCameraFile
                )
                takePictureLauncher.launch(uri)
            } catch (e: Exception) {
                Log.e("ProfileScreen", "Failed to launch camera", e)
            }
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    fun removePhoto() {
        try {
            if (avatarFile.exists()) avatarFile.delete()
        } catch (_: Exception) {}
        profilePicturePath = ""
        avatarBitmapKey = System.currentTimeMillis()
        val primaryName = sosContacts.firstOrNull()?.name ?: ""
        val primaryPhone = sosContacts.firstOrNull()?.phone ?: ""
        onSaveProfile(
            profile.copy(
                fullName = fullName,
                phoneNumber = phone,
                emergencyContactName = primaryName,
                emergencyContactPhone = primaryPhone,
                sosContacts = sosContacts,
                sleepScheduleAnswer = sleepScheduleSelection,
                sleepScheduleStart = sleepStartTime,
                sleepScheduleEnd = sleepEndTime,
                profilePicturePath = ""
            )
        )
    }

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

        // Profile Avatar Card with Interactive Camera & Zoom
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
                        .size(92.dp)
                        .clickable { showPhotoOptionsDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    // Circular Avatar Image or Fallback Icon
                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .clip(CircleShape)
                            .background(activeAccent.copy(alpha = 0.15f))
                            .border(2.dp, activeAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarBitmap != null) {
                            Image(
                                bitmap = avatarBitmap,
                                contentDescription = "Profile Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = activeAccent,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    // Camera Badge on bottom-right of avatar
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(if (colors.isDark) KavachGold else CyberCyan)
                            .border(2.dp, colors.card, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = "Camera Options",
                            tint = Color.Black,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (fullName.isNotBlank()) fullName else "Device Owner",
                    fontFamily = OrbitronFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = colors.textPrimary
                )
                if (phone.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION 1: PERSONAL DETAILS (Only Name and Phone Number)
        Text(
            text = "PERSONAL DETAILS",
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
            label = "Phone Number",
            value = phone,
            onValueChange = { phone = it },
            icon = Icons.Outlined.Phone,
            keyboardType = KeyboardType.Phone,
            colors = colors
        )

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION 2: SOS CONTACTS (Name left, Phone right, small green plus button underneath)
        Text(
            text = "SOS EMERGENCY CONTACTS",
            fontFamily = OrbitronFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = colors.textSecondary,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Distress contacts alerted during an intrusion or alarm trigger",
            fontSize = 12.sp,
            color = colors.textMuted
        )
        Spacer(modifier = Modifier.height(12.dp))

        sosContacts.forEachIndexed { index, contact ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Contact Name (Left)
                    OutlinedTextField(
                        value = contact.name,
                        onValueChange = { newName ->
                            val updated = sosContacts.toMutableList()
                            updated[index] = contact.copy(name = newName)
                            sosContacts = updated
                        },
                        label = { Text("Name", fontSize = 11.sp, color = colors.textSecondary) },
                        placeholder = { Text("Contact Name", fontSize = 12.sp, color = colors.textMuted) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeAccent,
                            unfocusedBorderColor = colors.cardBorder,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedContainerColor = colors.background.copy(alpha = 0.5f),
                            unfocusedContainerColor = colors.background.copy(alpha = 0.3f)
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Phone Number (Right)
                    OutlinedTextField(
                        value = contact.phone,
                        onValueChange = { newPhone ->
                            val updated = sosContacts.toMutableList()
                            updated[index] = contact.copy(phone = newPhone)
                            sosContacts = updated
                        },
                        label = { Text("Phone", fontSize = 11.sp, color = colors.textSecondary) },
                        placeholder = { Text("Phone Number", fontSize = 12.sp, color = colors.textMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeAccent,
                            unfocusedBorderColor = colors.cardBorder,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedContainerColor = colors.background.copy(alpha = 0.5f),
                            unfocusedContainerColor = colors.background.copy(alpha = 0.3f)
                        )
                    )

                    if (sosContacts.size > 1) {
                        IconButton(
                            onClick = {
                                val updated = sosContacts.toMutableList()
                                updated.removeAt(index)
                                sosContacts = updated
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Remove contact",
                                tint = CyberRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Small green plus button underneath the contact
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    val updated = sosContacts.toMutableList()
                    updated.add(SosContact(name = "", phone = ""))
                    sosContacts = updated
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberGreen,
                    contentColor = Color.Black
                ),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Add Contact",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "Add another contact",
                fontSize = 12.sp,
                color = CyberGreen,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    val updated = sosContacts.toMutableList()
                    updated.add(SosContact(name = "", phone = ""))
                    sosContacts = updated
                }
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // SECTION 3: SLEEP SCHEDULE
        Text(
            text = "SLEEP SCHEDULE",
            fontFamily = OrbitronFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = if (colors.isDark) KavachGold else CyberCyan,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Do you have a sleep schedule?",
            fontFamily = OrbitronFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(14.dp))

        if (sleepScheduleSelection == "Yes") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.5.dp, CyberGreen, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberGreen.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = CyberGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Yes, I have a schedule",
                            fontFamily = OrbitronFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = CyberGreen
                        )
                    }

                    Text(
                        text = "Change",
                        fontSize = 11.sp,
                        color = colors.textMuted,
                        modifier = Modifier.clickable { sleepScheduleSelection = "" }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Tap on Start or End time to open the real clock dial:",
                fontSize = 12.sp,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            val parsedStart = parseScheduleTime(sleepStartTime, 23, 0)
            val parsedEnd = parseScheduleTime(sleepEndTime, 7, 0)

            // Start Time Real Clock Card
            ProfileRealClockCard(
                label = "BEDTIME (START)",
                time = sleepStartTime,
                icon = Icons.Outlined.Bedtime,
                accentColor = Color(0xFFA855F7),
                colors = colors,
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, h, m ->
                            sleepStartTime = formatScheduleTime(h, m)
                        },
                        parsedStart.first,
                        parsedStart.second,
                        false
                    ).show()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // End Time Real Clock Card
            ProfileRealClockCard(
                label = "WAKING (END)",
                time = sleepEndTime,
                icon = Icons.Outlined.WbSunny,
                accentColor = CyberAmber,
                colors = colors,
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, h, m ->
                            sleepEndTime = formatScheduleTime(h, m)
                        },
                        parsedEnd.first,
                        parsedEnd.second,
                        false
                    ).show()
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Live Coverage Indicator
            val (isInRange, remainingTime) = getSleepRangeStatus(parsedStart, parsedEnd)
            if (isInRange) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, CyberGreen),
                    colors = CardDefaults.cardColors(containerColor = CyberGreen.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Bolt,
                            contentDescription = null,
                            tint = CyberGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "CURRENT TIME IS WITHIN RANGE",
                                fontFamily = OrbitronFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = CyberGreen
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Saving will activate Sleep Detection Mode immediately for the remaining ${remainingTime.first}h ${remainingTime.second}m!",
                                fontSize = 11.5.sp,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                    colors = CardDefaults.cardColors(containerColor = colors.background.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Bedtime,
                            contentDescription = null,
                            tint = Color(0xFFA855F7),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Kavach will automatically activate Sleep Mode daily from $sleepStartTime until $sleepEndTime.",
                            fontSize = 11.5.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileScheduleOptionRow(
                    label = "Yes",
                    subLabel = "Set regular bed & waking hours",
                    isSelected = sleepScheduleSelection == "Yes",
                    accentColor = CyberGreen,
                    colors = colors,
                    onClick = { sleepScheduleSelection = "Yes" }
                )

                ProfileScheduleOptionRow(
                    label = "Maybe",
                    subLabel = "Irregular or shift-based schedule",
                    isSelected = sleepScheduleSelection == "Maybe",
                    accentColor = CyberAmber,
                    colors = colors,
                    onClick = { sleepScheduleSelection = "Maybe" }
                )

                ProfileScheduleOptionRow(
                    label = "No",
                    subLabel = "Arm protection manually when needed",
                    isSelected = sleepScheduleSelection == "No",
                    accentColor = colors.textSecondary,
                    colors = colors,
                    onClick = { sleepScheduleSelection = "No" }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Save Button
        Button(
            onClick = {
                val cleanedContacts = sosContacts.filter { it.name.isNotBlank() || it.phone.isNotBlank() }
                val primaryName = cleanedContacts.firstOrNull()?.name ?: ""
                val primaryPhone = cleanedContacts.firstOrNull()?.phone ?: ""
                val updatedProfile = profile.copy(
                    fullName = fullName,
                    phoneNumber = phone,
                    emergencyContactName = primaryName,
                    emergencyContactPhone = primaryPhone,
                    sosContacts = cleanedContacts,
                    sleepScheduleAnswer = sleepScheduleSelection,
                    sleepScheduleStart = if (sleepScheduleSelection == "Yes") sleepStartTime else "",
                    sleepScheduleEnd = if (sleepScheduleSelection == "Yes") sleepEndTime else "",
                    profilePicturePath = profilePicturePath
                )
                onSaveProfile(updatedProfile)
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
                text = "✓ Profile details updated successfully",
                color = CyberGreen,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sign Out Button
        OutlinedButton(
            onClick = { showSignOutDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.2.dp, CyberRed.copy(alpha = 0.8f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = CyberRed
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = CyberRed
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SIGN OUT OF KAVACH",
                fontFamily = OrbitronFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = CyberRed
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
    }

    
    // Sign Out Confirmation Dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            containerColor = colors.card,
            title = {
                Text(
                    text = "SIGN OUT",
                    fontFamily = OrbitronFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to sign out? You will need your registered phone number and password to log back into Kavach on this or any replacement device.",
                    fontSize = 12.5.sp,
                    color = colors.textSecondary,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "SIGN OUT",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("CANCEL", color = colors.textMuted)
                }
            }
        )
    }

    // Photo Action Dialog (Camera vs Gallery vs View vs Remove)
    if (showPhotoOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoOptionsDialog = false },
            containerColor = colors.card,
            title = {
                Text(
                    text = "PROFILE PHOTO",
                    fontFamily = OrbitronFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.textPrimary
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (avatarBitmap != null) {
                        TextButton(
                            onClick = {
                                showPhotoOptionsDialog = false
                                showZoomOverlay = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.ZoomIn, contentDescription = null, tint = activeAccent)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Zoom & View Full Photo", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                        }
                    }

                    TextButton(
                        onClick = {
                            showPhotoOptionsDialog = false
                            launchCamera()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = activeAccent)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(if (avatarBitmap != null) "Take New Photo (Camera)" else "Take Photo (Camera)", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = {
                            showPhotoOptionsDialog = false
                            try {
                                pickGalleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            } catch (e: Exception) {
                                Log.e("ProfileScreen", "Failed to launch gallery picker", e)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Photo, contentDescription = null, tint = activeAccent)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Choose from Gallery", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                    }

                    if (avatarBitmap != null) {
                        TextButton(
                            onClick = {
                                showPhotoOptionsDialog = false
                                removePhoto()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = CyberRed)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Remove Photo", color = CyberRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPhotoOptionsDialog = false }) {
                    Text("CANCEL", color = colors.textMuted)
                }
            }
        )
    }

    // Interactive Fullscreen Zoom Overlay
    if (showZoomOverlay && avatarBitmap != null) {
        Dialog(
            onDismissRequest = { showZoomOverlay = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .systemBarsPadding()
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showZoomOverlay = false },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White)
                    }

                    Text(
                        text = "PROFILE PHOTO",
                        fontFamily = OrbitronFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )

                    if (scale > 1.05f) {
                        TextButton(
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            }
                        ) {
                            Text("RESET", color = if (colors.isDark) KavachGold else CyberCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(40.dp))
                    }
                }

                // Center Zoomable Image Viewport
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 80.dp)
                        .clipToBounds()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offset += pan
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1.2f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = avatarBitmap,
                        contentDescription = "Zoomed Profile Photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                }

                // Bottom Hint & Actions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp, start = 16.dp, end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Pinch to zoom • Double-tap to toggle 2.5x",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontFamily = RajdhaniFamily,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = {
                                showZoomOverlay = false
                                launchCamera()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = activeAccent),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Camera", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        OutlinedButton(
                            onClick = {
                                showZoomOverlay = false
                                try {
                                    pickGalleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                } catch (e: Exception) {
                                    Log.e("ProfileScreen", "Failed to launch gallery from zoom", e)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Outlined.Photo, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gallery", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileScheduleOptionRow(
    label: String,
    subLabel: String,
    isSelected: Boolean,
    accentColor: Color,
    colors: KavachColors,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) accentColor else colors.cardBorder
    val bgColor = if (isSelected) accentColor.copy(alpha = 0.12f) else colors.background.copy(alpha = 0.4f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, if (isSelected) accentColor else colors.textMuted, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = label,
                    fontFamily = OrbitronFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isSelected) accentColor else colors.textPrimary
                )
                Text(
                    text = subLabel,
                    fontSize = 11.5.sp,
                    color = colors.textSecondary
                )
            }
        }
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
    var view24h by remember { mutableStateOf(currentHour !in 9..21) }

    val hours = if (view24h) (0..23).toList() else (9..21).toList()
    val maxSeconds = 3600f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = colors.card)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GUARD ACTIVITY (TODAY)",
                    fontFamily = OrbitronFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )

                TextButton(
                    onClick = { view24h = !view24h },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (view24h) "Show 9A-9P" else "Show 24H",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (colors.isDark) KavachGold else CyberCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LegendItem("Pocket", pocketColor)
                LegendItem("Desk", deskColor)
                LegendItem("Sleep", sleepColor)
                LegendItem("Transit", travelColor)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Chart area with explicit Y-axis labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                // Left Y-Axis Labels
                Column(
                    modifier = Modifier
                        .width(32.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text("60m", fontSize = 9.sp, color = colors.textMuted, fontFamily = RajdhaniFamily)
                    Text("45m", fontSize = 9.sp, color = colors.textMuted.copy(alpha = 0.6f), fontFamily = RajdhaniFamily)
                    Text("30m", fontSize = 9.sp, color = colors.textMuted, fontFamily = RajdhaniFamily)
                    Text("15m", fontSize = 9.sp, color = colors.textMuted.copy(alpha = 0.6f), fontFamily = RajdhaniFamily)
                    Text("0m", fontSize = 9.sp, color = colors.textMuted, fontFamily = RajdhaniFamily)
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Chart Canvas
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    val w = size.width
                    val h = size.height
                    val slotWidth = w / hours.size

                    // Draw 60m top guide line
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.12f),
                        start = Offset(0f, 2f),
                        end = Offset(w, 2f),
                        strokeWidth = 1f
                    )

                    // Draw 45m guide line
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.08f),
                        start = Offset(0f, h * 0.25f),
                        end = Offset(w, h * 0.25f),
                        strokeWidth = 1f
                    )

                    // Draw 30m mid guide line
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.15f),
                        start = Offset(0f, h * 0.5f),
                        end = Offset(w, h * 0.5f),
                        strokeWidth = 1f
                    )

                    // Draw 15m guide line
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.08f),
                        start = Offset(0f, h * 0.75f),
                        end = Offset(w, h * 0.75f),
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

                            val segments = listOf(
                                pocketSecs to pocketColor,
                                deskSecs to deskColor,
                                sleepSecs to sleepColor,
                                travelSecs to travelColor
                            )

                            segments.forEach { (secs, color) ->
                                if (secs > 0) {
                                    val scaledHeight = (secs.toFloat() / maxSeconds) * h
                                    val segmentHeight = scaledHeight.coerceAtLeast(8f)
                                    drawRect(
                                        color = color,
                                        topLeft = Offset(xLeft, (currentY - segmentHeight).coerceAtLeast(0f)),
                                        size = Size(barWidth, segmentHeight)
                                    )
                                    currentY -= segmentHeight
                                }
                            }

                            if (hour == currentHour && isShieldArmed) {
                                drawCircle(
                                    color = pocketColor,
                                    radius = 3.5f,
                                    center = Offset(xCenter, (currentY - 8f).coerceAtLeast(8f))
                                )
                            }
                        } else {
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

@Composable
private fun ProfileRealClockCard(
    label: String,
    time: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    colors: KavachColors,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.2.dp, colors.cardBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = colors.background.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = label,
                        fontFamily = OrbitronFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = colors.textMuted,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = time,
                        fontFamily = RajdhaniFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = colors.textPrimary,
                        letterSpacing = 1.sp
                    )
                }
            }

            Surface(
                color = accentColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccessTime,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "CHOOSE",
                        fontFamily = OrbitronFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = accentColor
                    )
                }
            }
        }
    }
}

private fun formatScheduleTime(hour: Int, minute: Int): String {
    val amPm = if (hour >= 12) "PM" else "AM"
    val h = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return String.format("%02d:%02d %s", h, minute, amPm)
}

private fun parseScheduleTime(timeStr: String, fallbackHour: Int, fallbackMinute: Int): Pair<Int, Int> {
    return try {
        val trimmed = timeStr.trim().uppercase()
        val isPm = trimmed.contains("PM")
        val isAm = trimmed.contains("AM")
        val clean = trimmed.replace("AM", "").replace("PM", "").trim()
        val parts = clean.split(":")
        var hour = parts[0].trim().toInt()
        val minute = if (parts.size > 1) parts[1].trim().toInt() else 0
        if (isPm && hour < 12) hour += 12
        if (isAm && hour == 12) hour = 0
        Pair(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    } catch (_: Exception) {
        Pair(fallbackHour, fallbackMinute)
    }
}

private fun getSleepRangeStatus(start: Pair<Int, Int>, end: Pair<Int, Int>): Pair<Boolean, Pair<Int, Int>> {
    val now = Calendar.getInstance()
    val currentMinuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val startMinuteOfDay = start.first * 60 + start.second
    val endMinuteOfDay = end.first * 60 + end.second

    val isInWindow = if (startMinuteOfDay < endMinuteOfDay) {
        currentMinuteOfDay in startMinuteOfDay until endMinuteOfDay
    } else if (startMinuteOfDay > endMinuteOfDay) {
        currentMinuteOfDay >= startMinuteOfDay || currentMinuteOfDay < endMinuteOfDay
    } else {
        true
    }

    val remainingMinutes = if (!isInWindow) {
        0
    } else if (startMinuteOfDay < endMinuteOfDay) {
        endMinuteOfDay - currentMinuteOfDay
    } else {
        if (currentMinuteOfDay < endMinuteOfDay) {
            endMinuteOfDay - currentMinuteOfDay
        } else {
            (24 * 60 - currentMinuteOfDay) + endMinuteOfDay
        }
    }

    val hoursLeft = remainingMinutes / 60
    val minsLeft = remainingMinutes % 60
    return Pair(isInWindow, Pair(hoursLeft, minsLeft))
}
