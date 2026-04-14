package com.turnit.ide.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch

/**
 * LoginScreen: Premium Jetpack Compose UI for TurnIt IDE authentication.
 * 
 * Features:
 * - Email and password input fields with validation feedback
 * - Primary "Login" button with TurnIt IDE branding colors
 * - Secondary "Login with Biometrics" button with fingerprint icon
 * - BiometricPrompt integration for secure device authentication
 * - Snackbar feedback for user actions
 * 
 * @param onLoginSuccess Callback triggered when either email/password or biometric login succeeds
 * @param onNavigateToSignup Callback for signup navigation
 */
@Composable
fun LoginScreen(
    onLoginSuccess: (email: String) -> Unit = {},
    onNavigateToSignup: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Form state
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IdeColors.Bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- HEADER: TurnIt IDE Logo & Title ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Branding logo placeholder
                    Box(
                        modifier = Modifier
                            .width(72.dp)
                            .height(72.dp)
                            .background(
                                color = IdeColors.AccentBlue,
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "TI",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = IdeColors.Bg
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "TurnIt IDE",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = IdeColors.TextPrimary
                    )
                    Text(
                        text = "Secure Terminal Development Environment",
                        fontSize = 12.sp,
                        color = IdeColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // --- EMAIL FIELD ---
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email Address") },
                placeholder = { Text("you@example.com") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Mail,
                        contentDescription = "Email",
                        tint = IdeColors.AccentBlue
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedLabelColor = IdeColors.AccentBlue,
                    unfocusedLabelColor = IdeColors.TextSecondary,
                    focusedBorderColor = IdeColors.AccentBlue,
                    unfocusedBorderColor = IdeColors.Border,
                    cursorColor = IdeColors.AccentBlue,
                    focusedTextColor = IdeColors.TextPrimary,
                    unfocusedTextColor = IdeColors.TextPrimary,
                    focusedLeadingIconColor = IdeColors.AccentBlue,
                    unfocusedLeadingIconColor = IdeColors.TextSecondary
                ),
                shape = RoundedCornerShape(8.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // --- PASSWORD FIELD ---
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                placeholder = { Text("••••••••") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Password",
                        tint = IdeColors.AccentBlue
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = { isPasswordVisible = !isPasswordVisible }
                    ) {
                        Icon(
                            imageVector = if (isPasswordVisible)
                                Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = "Toggle password visibility",
                            tint = IdeColors.TextSecondary
                        )
                    }
                },
                visualTransformation = if (isPasswordVisible)
                    VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedLabelColor = IdeColors.AccentBlue,
                    unfocusedLabelColor = IdeColors.TextSecondary,
                    focusedBorderColor = IdeColors.AccentBlue,
                    unfocusedBorderColor = IdeColors.Border,
                    cursorColor = IdeColors.AccentBlue,
                    focusedTextColor = IdeColors.TextPrimary,
                    unfocusedTextColor = IdeColors.TextPrimary,
                    focusedLeadingIconColor = IdeColors.AccentBlue,
                    unfocusedLeadingIconColor = IdeColors.TextSecondary
                ),
                shape = RoundedCornerShape(8.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // --- PRIMARY LOGIN BUTTON ---
            Button(
                onClick = {
                    if (email.isNotEmpty() && password.isNotEmpty()) {
                        isLoading = true
                        scope.launch {
                            // Simulate network request
                            kotlinx.coroutines.delay(1000)
                            isLoading = false
                            snackbarHostState.showSnackbar(
                                message = "Login successful for $email",
                                duration = SnackbarDuration.Short
                            )
                            onLoginSuccess(email)
                        }
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Please enter email and password",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = IdeColors.AccentBlue,
                    contentColor = IdeColors.Bg,
                    disabledContainerColor = IdeColors.TextMuted,
                    disabledContentColor = IdeColors.Bg
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isLoading) "Logging in..." else "Login",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // --- SECONDARY BIOMETRICS BUTTON ---
            Button(
                onClick = {
                    if (context is FragmentActivity) {
                        triggerBiometricPrompt(
                            activity = context,
                            onSuccess = { email ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "Biometric login successful",
                                        duration = SnackbarDuration.Short
                                    )
                                    onLoginSuccess(email)
                                }
                            },
                            onError = { errorMessage ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = errorMessage,
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = IdeColors.AccentBlue
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = "Biometric Login",
                        tint = IdeColors.AccentBlue,
                        modifier = Modifier
                            .width(20.dp)
                            .height(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Login with Biometrics",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // --- FOOTER: Sign Up Link ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Don't have an account? ",
                    fontSize = 13.sp,
                    color = IdeColors.TextSecondary
                )
                Text(
                    text = "Sign Up",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IdeColors.AccentBlue,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
        
        // --- SNACKBAR HOST ---
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            snackbar = { snackbarData ->
                Snackbar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = IdeColors.Border,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    containerColor = IdeColors.BgElevated,
                    contentColor = IdeColors.TextPrimary,
                    actionColor = IdeColors.AccentBlue
                ) {
                    Text(snackbarData.visibleDuration.toString())
                    Text(snackbarData.message)
                }
            }
        )
    }
}

/**
 * Helper function: Trigger Android BiometricPrompt for secure authentication.
 * 
 * Checks device biometric capabilities, initializes the prompt executor,
 * and handles authentication success/failure callbacks.
 * 
 * @param activity FragmentActivity context required for BiometricPrompt
 * @param onSuccess Callback with authenticated user email
 * @param onError Callback with error message
 */
fun triggerBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: (email: String) -> Unit,
    onError: (errorMessage: String) -> Unit
) {
    // Check if device supports biometrics
    val biometricManager = BiometricManager.from(activity)
    when (biometricManager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    )) {
        BiometricManager.BIOMETRIC_SUCCESS -> {
            // Device supports biometric auth, proceed
            createAndShowBiometricPrompt(activity, onSuccess, onError)
        }
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
            onError("No biometric hardware available on this device")
        }
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
            onError("Biometric hardware is currently unavailable")
        }
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
            onError("No biometric data enrolled. Please set up fingerprint in device settings.")
        }
        else -> {
            onError("Biometric authentication is not available")
        }
    }
}

/**
 * Initialize and display the BiometricPrompt dialog.
 * 
 * Sets up the prompt UI with title, subtitle, and negative button,
 * then initiates the authentication flow.
 * 
 * @param activity FragmentActivity for the prompt execution
 * @param onSuccess Callback on successful authentication
 * @param onError Callback on authentication failure
 */
private fun createAndShowBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: (email: String) -> Unit,
    onError: (errorMessage: String) -> Unit
) {
    val biometricPrompt = BiometricPrompt(
        activity,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                // Simulate authenticated user email from secure storage
                // In production, retrieve from TokenManager or secure preferences
                onSuccess("authenticated.user@turnit.dev")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError("Authentication failed: $errString")
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Biometric authentication failed. Please try again.")
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("TurnIt IDE Login")
        .setSubtitle("Authenticate with your biometric")
        .setDescription("Use your fingerprint to securely login to TurnIt IDE")
        .setNegativeButtonText("Cancel")
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .build()

    biometricPrompt.authenticate(promptInfo)
}
