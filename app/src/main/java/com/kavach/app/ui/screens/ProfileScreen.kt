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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.app.TimePickerDialog
import com.kavach.app.data.KavachSettings
import com.kavach.app.data.NightSchedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
    settings: KavachSettings = KavachSettings(),
    onSaveProfile: (UserProfile) -> Unit,
    onSettingsChanged: (KavachSettings) -> Unit = {},
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
    var profilePicturePath by remember(profile.profilePicturePath) { mutableStateOf(profile.profilePicturePath) }

    var isSavedSnackbarVisible by remember { mutableStateOf(false) }
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
                onSaveProfile(
                    profile.copy(
                        fullName = fullName,
                        email = email,
                        phoneNumber = phone,
                        emergencyContactName = emergencyName,
                        emergencyContactPhone = emergencyPhone,
                        bloodGroup = bloodGroup,
                        homeAddress = address,
                        notes = notes,
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
        onSaveProfile(
            profile.copy(
                fullName = fullName,
                email = email,
                phoneNumber = phone,
                emergencyContactName = emergencyName,
                emergencyContactPhone = emergencyPhone,
                bloodGroup = bloodGroup,
                homeAddress = address,
                notes = notes,
                profilePicturePath = ""
            )
        )
    }

    fun formatScheduleTime(hour: Int, minute: Int): String {
        val amPm = if (hour >= 12) "PM" else "AM"
        val h = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format("%02d:%02d %s", h, minute, amPm)
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
                        notes = notes,
                        profilePicturePath = profilePicturePath
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

        Spacer(modifier = Modifier.height(28.dp))

        // Section: Automatic Sleep Guard Schedule
        Text(
            text = "AUTOMATIC SLEEP GUARD SCHEDULE",
            fontFamily = OrbitronFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = if (colors.isDark) KavachGold else CyberCyan
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = colors.card)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header Row with Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFA855F7).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Bedtime,
                                contentDescription = null,
                                tint = Color(0xFFA855F7),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Auto Sleep Mode",
                                fontFamily = OrbitronFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = colors.textPrimary
                            )
                            Text(
                                text = if (settings.nightSchedule.enabled) "Active from ${formatScheduleTime(settings.nightSchedule.startHour, settings.nightSchedule.startMinute)} to ${formatScheduleTime(settings.nightSchedule.endHour, settings.nightSchedule.endMinute)}" else "Disabled",
                                fontSize = 11.5.sp,
                                color = colors.textSecondary
                            )
                        }
                    }

                    Switch(
                        checked = settings.nightSchedule.enabled,
                        onCheckedChange = { isEnabled ->
                            onSettingsChanged(
                                settings.copy(
                                    nightSchedule = settings.nightSchedule.copy(enabled = isEnabled)
                                )
                            )
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFFA855F7)
                        )
                    )
                }

                if (settings.nightSchedule.enabled) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = colors.cardBorder.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Start Time (X Time) & End Time (Y Time) Picker Selectors
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Start Time Selector
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.background.copy(alpha = 0.6f))
                                .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    TimePickerDialog(
                                        context,
                                        { _, hour, minute ->
                                            onSettingsChanged(
                                                settings.copy(
                                                    nightSchedule = settings.nightSchedule.copy(
                                                        startHour = hour,
                                                        startMinute = minute
                                                    )
                                                )
                                            )
                                        },
                                        settings.nightSchedule.startHour,
                                        settings.nightSchedule.startMinute,
                                        false
                                    ).show()
                                }
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "START TIME (X)",
                                fontSize = 10.sp,
                                color = colors.textMuted,
                                fontWeight = FontWeight.Bold,
                                fontFamily = OrbitronFamily
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.AccessTime, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = formatScheduleTime(settings.nightSchedule.startHour, settings.nightSchedule.startMinute),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                    fontFamily = RajdhaniFamily
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // End Time Selector
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.background.copy(alpha = 0.6f))
                                .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    TimePickerDialog(
                                        context,
                                        { _, hour, minute ->
                                            onSettingsChanged(
                                                settings.copy(
                                                    nightSchedule = settings.nightSchedule.copy(
                                                        endHour = hour,
                                                        endMinute = minute
                                                    )
                                                )
                                            )
                                        },
                                        settings.nightSchedule.endHour,
                                        settings.nightSchedule.endMinute,
                                        false
                                    ).show()
                                }
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "END TIME (Y)",
                                fontSize = 10.sp,
                                color = colors.textMuted,
                                fontWeight = FontWeight.Bold,
                                fontFamily = OrbitronFamily
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.AccessTime, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = formatScheduleTime(settings.nightSchedule.endHour, settings.nightSchedule.endMinute),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                    fontFamily = RajdhaniFamily
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "🌙 At the start time, Kavach automatically arms in Sleep Mode with nocturnal bed-bounce filtering. At the end time, it automatically disarms.",
                        fontSize = 11.5.sp,
                        color = colors.textMuted,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
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

