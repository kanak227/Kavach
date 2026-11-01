package com.kavach.app.ui.screens

import android.widget.Toast
import android.app.TimePickerDialog
import java.util.Calendar
import androidx.compose.animation.*
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kavach.app.data.SosContact
import com.kavach.app.data.UserProfile
import com.kavach.app.ui.theme.*

private enum class AuthMode {
    SIGN_UP,
    LOGIN
}

private enum class SignUpStep {
    PHONE_INPUT,
    OTP_VERIFY,
    NAME_AND_PASSWORD,
    SOS_CONTACTS,
    SLEEP_SCHEDULE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpFlowScreen(
    initialPhone: String = "",
    onSignUpComplete: (UserProfile) -> Unit,
    onLogInWithPassword: (String, String) -> Boolean = { _, _ -> false }
) {
    val context = LocalContext.current
    val colors = LocalKavachColors.current

    var authMode by remember { mutableStateOf(AuthMode.SIGN_UP) }
    var currentStep by remember { mutableStateOf(SignUpStep.PHONE_INPUT) }

    // State for Login Mode
    var loginPhoneInput by remember { mutableStateOf(initialPhone) }
    var loginPasswordInput by remember { mutableStateOf("") }
    var loginPasswordVisible by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }

    // State for Sign Up: Phone & OTP
    var phoneNumberInput by remember { mutableStateOf(initialPhone) }
    var generatedOtp by remember { mutableStateOf("") }
    var enteredOtp by remember { mutableStateOf("") }
    var otpError by remember { mutableStateOf<String?>(null) }

    // State for Sign Up: Personal Details & Password
    var fullNameInput by remember { mutableStateOf("") }
    var confirmedPhoneInput by remember { mutableStateOf("") }
    var signUpPasswordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var signUpPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var nameOrPasswordError by remember { mutableStateOf<String?>(null) }

    // State for Sign Up: SOS Contacts
    var sosContacts by remember {
        mutableStateOf(
            listOf(
                SosContact(name = "", phone = "")
            )
        )
    }

    // State for Sign Up: Sleep Schedule
    var sleepScheduleSelection by remember { mutableStateOf<String?>(null) }
    var sleepStartTime by remember { mutableStateOf("11:00 PM") }
    var sleepEndTime by remember { mutableStateOf("07:00 AM") }

    val activeAccent = if (colors.isDark) KavachGold else CyberCyan

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Branding Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(activeAccent.copy(alpha = 0.15f))
                        .border(1.5.dp, activeAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = activeAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "KAVACH",
                        fontFamily = OrbitronFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        letterSpacing = 3.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "STEALTH PROTECTION",
                        fontFamily = RajdhaniFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 2.sp,
                        color = activeAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Glassmorphic Card Container
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.5.dp, colors.cardBorder, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = colors.card),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Top Switcher: Log In vs Sign Up
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.background.copy(alpha = 0.6f))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (authMode == AuthMode.LOGIN) activeAccent else Color.Transparent)
                                    .clickable {
                                        authMode = AuthMode.LOGIN
                                        loginError = null
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "LOG IN",
                                    fontFamily = OrbitronFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (authMode == AuthMode.LOGIN) (if (colors.isDark) Color.Black else Color.White) else colors.textMuted
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (authMode == AuthMode.SIGN_UP) activeAccent else Color.Transparent)
                                    .clickable {
                                        authMode = AuthMode.SIGN_UP
                                        currentStep = SignUpStep.PHONE_INPUT
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "SIGN UP",
                                    fontFamily = OrbitronFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (authMode == AuthMode.SIGN_UP) (if (colors.isDark) Color.Black else Color.White) else colors.textMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        if (authMode == AuthMode.LOGIN) {
                            // -------------------------------------------------------------
                            // LOGIN WINDOW (Phone Number + Password)
                            // -------------------------------------------------------------
                            LoginContent(
                                phone = loginPhoneInput,
                                onPhoneChange = {
                                    loginPhoneInput = it
                                    loginError = null
                                },
                                password = loginPasswordInput,
                                onPasswordChange = {
                                    loginPasswordInput = it
                                    loginError = null
                                },
                                isPasswordVisible = loginPasswordVisible,
                                onTogglePasswordVisibility = { loginPasswordVisible = !loginPasswordVisible },
                                error = loginError,
                                activeAccent = activeAccent,
                                colors = colors,
                                onLogin = {
                                    if (loginPhoneInput.trim().length < 7) {
                                        loginError = "Please enter a valid phone number"
                                    } else if (loginPasswordInput.isBlank()) {
                                        loginError = "Please enter your password"
                                    } else {
                                        val success = onLogInWithPassword(loginPhoneInput.trim(), loginPasswordInput)
                                        if (!success) {
                                            loginError = "Invalid phone number or password. If you lost your phone, verify your number & password or sign up."
                                        }
                                    }
                                },
                                onSwitchToSignUp = {
                                    authMode = AuthMode.SIGN_UP
                                    currentStep = SignUpStep.PHONE_INPUT
                                }
                            )
                        } else {
                            // -------------------------------------------------------------
                            // SIGN UP FLOW
                            // -------------------------------------------------------------
                            when (currentStep) {
                                SignUpStep.PHONE_INPUT -> {
                                    PhoneInputContent(
                                        phoneNumber = phoneNumberInput,
                                        onPhoneChange = { phoneNumberInput = it },
                                        activeAccent = activeAccent,
                                        colors = colors,
                                        onSendOtp = {
                                            val cleaned = phoneNumberInput.trim()
                                            if (cleaned.length < 7) {
                                                Toast.makeText(context, "Please enter a valid phone number", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val otp = (1000..9999).random().toString()
                                                generatedOtp = otp
                                                confirmedPhoneInput = cleaned
                                                enteredOtp = ""
                                                otpError = null
                                                currentStep = SignUpStep.OTP_VERIFY
                                                Toast.makeText(context, "Kavach OTP Code: $otp", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        onSwitchToLogin = {
                                            authMode = AuthMode.LOGIN
                                            loginError = null
                                        }
                                    )
                                }

                                SignUpStep.OTP_VERIFY -> {
                                    OtpVerifyContent(
                                        phoneNumber = phoneNumberInput,
                                        generatedOtp = generatedOtp,
                                        enteredOtp = enteredOtp,
                                        onOtpChange = {
                                            enteredOtp = it
                                            otpError = null
                                        },
                                        error = otpError,
                                        activeAccent = activeAccent,
                                        colors = colors,
                                        onBackToPhone = { currentStep = SignUpStep.PHONE_INPUT },
                                        onVerify = {
                                            if (enteredOtp.trim() == generatedOtp) {
                                                currentStep = SignUpStep.NAME_AND_PASSWORD
                                            } else {
                                                otpError = "Invalid verification code. Please check code: $generatedOtp"
                                            }
                                        }
                                    )
                                }

                                SignUpStep.NAME_AND_PASSWORD -> {
                                    NameAndPasswordContent(
                                        name = fullNameInput,
                                        onNameChange = {
                                            fullNameInput = it
                                            nameOrPasswordError = null
                                        },
                                        phone = confirmedPhoneInput,
                                        onPhoneChange = { confirmedPhoneInput = it },
                                        password = signUpPasswordInput,
                                        onPasswordChange = {
                                            signUpPasswordInput = it
                                            nameOrPasswordError = null
                                        },
                                        isPasswordVisible = signUpPasswordVisible,
                                        onTogglePasswordVisibility = { signUpPasswordVisible = !signUpPasswordVisible },
                                        confirmPassword = confirmPasswordInput,
                                        onConfirmPasswordChange = {
                                            confirmPasswordInput = it
                                            nameOrPasswordError = null
                                        },
                                        isConfirmPasswordVisible = confirmPasswordVisible,
                                        onToggleConfirmPasswordVisibility = { confirmPasswordVisible = !confirmPasswordVisible },
                                        error = nameOrPasswordError,
                                        activeAccent = activeAccent,
                                        colors = colors,
                                        onNext = {
                                            if (fullNameInput.trim().isBlank()) {
                                                nameOrPasswordError = "Please enter your full name"
                                            } else if (signUpPasswordInput.length < 4) {
                                                nameOrPasswordError = "Password must be at least 4 characters"
                                            } else if (signUpPasswordInput != confirmPasswordInput) {
                                                nameOrPasswordError = "Passwords do not match"
                                            } else {
                                                currentStep = SignUpStep.SOS_CONTACTS
                                            }
                                        }
                                    )
                                }

                                SignUpStep.SOS_CONTACTS -> {
                                    SosContactsContent(
                                        contacts = sosContacts,
                                        onContactsChange = { sosContacts = it },
                                        activeAccent = activeAccent,
                                        colors = colors,
                                        onNext = {
                                            currentStep = SignUpStep.SLEEP_SCHEDULE
                                        }
                                    )
                                }

                                SignUpStep.SLEEP_SCHEDULE -> {
                                    SleepScheduleContent(
                                        selection = sleepScheduleSelection,
                                        onSelectionChange = { sleepScheduleSelection = it },
                                        startTime = sleepStartTime,
                                        onStartTimeChange = { sleepStartTime = it },
                                        endTime = sleepEndTime,
                                        onEndTimeChange = { sleepEndTime = it },
                                        activeAccent = activeAccent,
                                        colors = colors,
                                        onFinish = {
                                            val answer = sleepScheduleSelection ?: "No"
                                            val cleanedContacts = sosContacts.filter { it.name.isNotBlank() || it.phone.isNotBlank() }
                                            val finalProfile = UserProfile(
                                                fullName = fullNameInput.trim(),
                                                phoneNumber = confirmedPhoneInput.trim(),
                                                emergencyContactName = cleanedContacts.firstOrNull()?.name ?: "",
                                                emergencyContactPhone = cleanedContacts.firstOrNull()?.phone ?: "",
                                                sosContacts = cleanedContacts,
                                                sleepScheduleAnswer = answer,
                                                sleepScheduleStart = if (answer == "Yes") sleepStartTime else "",
                                                sleepScheduleEnd = if (answer == "Yes") sleepEndTime else "",
                                                password = signUpPasswordInput
                                            )
                                            onSignUpComplete(finalProfile)
                                        }
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

// -------------------------------------------------------------
// LOGIN CONTENT (Phone Number + Password)
// -------------------------------------------------------------
@Composable
private fun LoginContent(
    phone: String,
    onPhoneChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isPasswordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    error: String?,
    activeAccent: Color,
    colors: KavachColors,
    onLogin: () -> Unit,
    onSwitchToSignUp: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "LOG IN TO KAVACH",
            fontFamily = OrbitronFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Log in with your registered phone number and password to restore protection on this phone.",
            fontSize = 12.sp,
            color = colors.textSecondary,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Phone Input
        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            label = { Text("Phone Number", color = colors.textSecondary) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Phone,
                    contentDescription = null,
                    tint = activeAccent
                )
            },
            placeholder = { Text("+91 98765 43210", color = colors.textMuted) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = activeAccent,
                unfocusedBorderColor = colors.cardBorder,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedContainerColor = colors.background.copy(alpha = 0.5f),
                unfocusedContainerColor = colors.background.copy(alpha = 0.3f)
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Password Input
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password", color = colors.textSecondary) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = activeAccent
                )
            },
            trailingIcon = {
                IconButton(onClick = onTogglePasswordVisibility) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = "Toggle password visibility",
                        tint = colors.textMuted
                    )
                }
            },
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = activeAccent,
                unfocusedBorderColor = colors.cardBorder,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedContainerColor = colors.background.copy(alpha = 0.5f),
                unfocusedContainerColor = colors.background.copy(alpha = 0.3f)
            )
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = CyberRed,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Bottom Row: Switch to Sign Up on left, Log In on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onSwitchToSignUp) {
                Text(
                    text = "New user? Sign Up",
                    color = activeAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick = onLogin,
                colors = ButtonDefaults.buttonColors(
                    containerColor = activeAccent,
                    contentColor = if (colors.isDark) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(46.dp)
            ) {
                Text(
                    text = "LOG IN",
                    fontFamily = OrbitronFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 1A: Phone Number Input
// -------------------------------------------------------------
@Composable
private fun PhoneInputContent(
    phoneNumber: String,
    onPhoneChange: (String) -> Unit,
    activeAccent: Color,
    colors: KavachColors,
    onSendOtp: () -> Unit,
    onSwitchToLogin: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "PHONE SIGN UP",
            fontFamily = OrbitronFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Enter your mobile phone number to receive a secure Kavach verification code.",
            fontSize = 12.sp,
            color = colors.textSecondary,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = onPhoneChange,
            label = { Text("Phone Number", color = colors.textSecondary) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Phone,
                    contentDescription = null,
                    tint = activeAccent
                )
            },
            placeholder = { Text("+91 98765 43210", color = colors.textMuted) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = activeAccent,
                unfocusedBorderColor = colors.cardBorder,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedContainerColor = colors.background.copy(alpha = 0.5f),
                unfocusedContainerColor = colors.background.copy(alpha = 0.3f)
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Bottom Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onSwitchToLogin) {
                Text(
                    text = "Have account? Log In",
                    color = colors.textMuted,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onSendOtp,
                colors = ButtonDefaults.buttonColors(
                    containerColor = activeAccent,
                    contentColor = if (colors.isDark) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(46.dp)
            ) {
                Text(
                    text = "GET OTP",
                    fontFamily = OrbitronFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 1B: OTP Verification
// -------------------------------------------------------------
@Composable
private fun OtpVerifyContent(
    phoneNumber: String,
    generatedOtp: String,
    enteredOtp: String,
    onOtpChange: (String) -> Unit,
    error: String?,
    activeAccent: Color,
    colors: KavachColors,
    onBackToPhone: () -> Unit,
    onVerify: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "VERIFY OTP",
            fontFamily = OrbitronFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Enter the 4-digit code sent to $phoneNumber",
            fontSize = 12.sp,
            color = colors.textSecondary
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Demo OTP badge helper
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CyberGreen.copy(alpha = 0.12f))
                .border(1.dp, CyberGreen.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Key,
                    contentDescription = null,
                    tint = CyberGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Demo Verification Code: $generatedOtp",
                    color = CyberGreen,
                    fontFamily = OrbitronFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        OutlinedTextField(
            value = enteredOtp,
            onValueChange = { if (it.length <= 4) onOtpChange(it) },
            label = { Text("4-Digit OTP", color = colors.textSecondary) },
            placeholder = { Text("e.g. $generatedOtp", color = colors.textMuted) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = activeAccent
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            isError = error != null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = activeAccent,
                unfocusedBorderColor = colors.cardBorder,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedContainerColor = colors.background.copy(alpha = 0.5f),
                unfocusedContainerColor = colors.background.copy(alpha = 0.3f)
            )
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = error,
                color = CyberRed,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Bottom Row: Resend/Change Phone on left, Verify on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBackToPhone) {
                Text(
                    text = "Change Phone",
                    color = colors.textMuted,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onVerify,
                colors = ButtonDefaults.buttonColors(
                    containerColor = activeAccent,
                    contentColor = if (colors.isDark) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(46.dp)
            ) {
                Text(
                    text = "VERIFY",
                    fontFamily = OrbitronFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 2: Name, Phone & Password Window
// -------------------------------------------------------------
@Composable
private fun NameAndPasswordContent(
    name: String,
    onNameChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isPasswordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    isConfirmPasswordVisible: Boolean,
    onToggleConfirmPasswordVisibility: () -> Unit,
    error: String?,
    activeAccent: Color,
    colors: KavachColors,
    onNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "USER & SECURITY SETUP",
            fontFamily = OrbitronFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Set up your identity and a recovery password to access your account if your device is lost or replaced.",
            fontSize = 12.sp,
            color = colors.textSecondary,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Name Field
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Full Name", color = colors.textSecondary) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = activeAccent
                )
            },
            placeholder = { Text("e.g. Alex Drake", color = colors.textMuted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = activeAccent,
                unfocusedBorderColor = colors.cardBorder,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedContainerColor = colors.background.copy(alpha = 0.5f),
                unfocusedContainerColor = colors.background.copy(alpha = 0.3f)
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Phone Field
        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            label = { Text("Phone Number", color = colors.textSecondary) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Phone,
                    contentDescription = null,
                    tint = activeAccent
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = activeAccent,
                unfocusedBorderColor = colors.cardBorder,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedContainerColor = colors.background.copy(alpha = 0.5f),
                unfocusedContainerColor = colors.background.copy(alpha = 0.3f)
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Password Field
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Create Security Password", color = colors.textSecondary) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = activeAccent
                )
            },
            trailingIcon = {
                IconButton(onClick = onTogglePasswordVisibility) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = "Toggle password visibility",
                        tint = colors.textMuted
                    )
                }
            },
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = activeAccent,
                unfocusedBorderColor = colors.cardBorder,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedContainerColor = colors.background.copy(alpha = 0.5f),
                unfocusedContainerColor = colors.background.copy(alpha = 0.3f)
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Confirm Password Field
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = { Text("Confirm Password", color = colors.textSecondary) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.CheckCircleOutline,
                    contentDescription = null,
                    tint = activeAccent
                )
            },
            trailingIcon = {
                IconButton(onClick = onToggleConfirmPasswordVisibility) {
                    Icon(
                        imageVector = if (isConfirmPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = "Toggle password visibility",
                        tint = colors.textMuted
                    )
                }
            },
            visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = activeAccent,
                unfocusedBorderColor = colors.cardBorder,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedContainerColor = colors.background.copy(alpha = 0.5f),
                unfocusedContainerColor = colors.background.copy(alpha = 0.3f)
            )
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = error,
                color = CyberRed,
                fontSize = 11.5.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Bottom Right Next Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(
                    containerColor = activeAccent,
                    contentColor = if (colors.isDark) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(46.dp)
            ) {
                Text(
                    text = "NEXT",
                    fontFamily = OrbitronFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 3: SOS Contacts Window
// -------------------------------------------------------------
@Composable
private fun SosContactsContent(
    contacts: List<SosContact>,
    onContactsChange: (List<SosContact>) -> Unit,
    activeAccent: Color,
    colors: KavachColors,
    onNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "SOS CONTACTS",
            fontFamily = OrbitronFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Add contacts to receive emergency distress alerts and telemetry when an intrusion or theft is triggered.",
            fontSize = 12.sp,
            color = colors.textSecondary,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Dynamic Contacts List
        contacts.forEachIndexed { index, contact ->
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
                            val updated = contacts.toMutableList()
                            updated[index] = contact.copy(name = newName)
                            onContactsChange(updated)
                        },
                        label = { Text("Name", fontSize = 11.sp, color = colors.textSecondary) },
                        placeholder = { Text("Name", fontSize = 12.sp, color = colors.textMuted) },
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
                            val updated = contacts.toMutableList()
                            updated[index] = contact.copy(phone = newPhone)
                            onContactsChange(updated)
                        },
                        label = { Text("Phone", fontSize = 11.sp, color = colors.textSecondary) },
                        placeholder = { Text("Number", fontSize = 12.sp, color = colors.textMuted) },
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

                    // Remove icon if more than 1 contact
                    if (contacts.size > 1) {
                        IconButton(
                            onClick = {
                                val updated = contacts.toMutableList()
                                updated.removeAt(index)
                                onContactsChange(updated)
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

        Spacer(modifier = Modifier.height(10.dp))

        // Small green plus button underneath the contact to add as much as user wants
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    val updated = contacts.toMutableList()
                    updated.add(SosContact(name = "", phone = ""))
                    onContactsChange(updated)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberGreen,
                    contentColor = Color.Black
                ),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Add Contact",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "Add another contact",
                fontSize = 12.sp,
                color = CyberGreen,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    val updated = contacts.toMutableList()
                    updated.add(SosContact(name = "", phone = ""))
                    onContactsChange(updated)
                }
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Bottom Right Next Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(
                    containerColor = activeAccent,
                    contentColor = if (colors.isDark) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(46.dp)
            ) {
                Text(
                    text = "NEXT",
                    fontFamily = OrbitronFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 4: Sleep Schedule Window
// -------------------------------------------------------------
@Composable
private fun SleepScheduleContent(
    selection: String?,
    onSelectionChange: (String) -> Unit,
    startTime: String,
    onStartTimeChange: (String) -> Unit,
    endTime: String,
    onEndTimeChange: (String) -> Unit,
    activeAccent: Color,
    colors: KavachColors,
    onFinish: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "SLEEP SCHEDULE",
            fontFamily = OrbitronFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(10.dp))

        // The question
        Text(
            text = "Do you have a sleep schedule?",
            fontFamily = OrbitronFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = if (colors.isDark) KavachGold else CyberCyan
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (selection == "Yes") {
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
                        modifier = Modifier.clickable { onSelectionChange("") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Tap on Start or End time to open the real clock dial:",
                fontSize = 12.sp,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            val context = LocalContext.current
            val parsedStart = parseScheduleTime(startTime, 23, 0)
            val parsedEnd = parseScheduleTime(endTime, 7, 0)

            // Start Time Real Clock Card
            RealClockPickerCard(
                label = "BEDTIME (START)",
                time = startTime,
                icon = Icons.Outlined.Bedtime,
                accentColor = Color(0xFFA855F7),
                colors = colors,
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, h, m ->
                            onStartTimeChange(formatScheduleTime(h, m))
                        },
                        parsedStart.first,
                        parsedStart.second,
                        false
                    ).show()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // End Time Real Clock Card
            RealClockPickerCard(
                label = "WAKING (END)",
                time = endTime,
                icon = Icons.Outlined.WbSunny,
                accentColor = CyberAmber,
                colors = colors,
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, h, m ->
                            onEndTimeChange(formatScheduleTime(h, m))
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
                                text = "Kavach will immediately activate Sleep Detection Mode for the remaining ${remainingTime.first}h ${remainingTime.second}m!",
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
                            text = "Kavach will automatically activate Sleep Mode daily from $startTime until $endTime.",
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
                ScheduleOptionRow(
                    label = "Yes",
                    subLabel = "Set regular bed & waking hours",
                    isSelected = selection == "Yes",
                    accentColor = CyberGreen,
                    colors = colors,
                    onClick = { onSelectionChange("Yes") }
                )

                ScheduleOptionRow(
                    label = "Maybe",
                    subLabel = "Irregular or shift-based schedule",
                    isSelected = selection == "Maybe",
                    accentColor = CyberAmber,
                    colors = colors,
                    onClick = { onSelectionChange("Maybe") }
                )

                ScheduleOptionRow(
                    label = "No",
                    subLabel = "Arm protection manually when needed",
                    isSelected = selection == "No",
                    accentColor = colors.textSecondary,
                    colors = colors,
                    onClick = { onSelectionChange("No") }
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Bottom right Finish Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onFinish,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberGreen,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(46.dp)
            ) {
                Text(
                    text = "FINISH",
                    fontFamily = OrbitronFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.Black
                )
            }
        }
    }
}

@Composable
private fun ScheduleOptionRow(
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
private fun RealClockPickerCard(
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
