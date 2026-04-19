package com.turnit.ide.ui

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turnit.ide.auth.FirebaseAuthManager
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    authManager: FirebaseAuthManager,
    onAuthenticated: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IdeColors.Bg)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedRgbLogo()
            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(16.dp)
                    )
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.2f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Cloud Sign In",
                    color = IdeColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(12.dp),
                    colors = outlinedFieldColors()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp),
                    colors = outlinedFieldColors()
                )

                Button(
                    onClick = {
                        scope.launch {
                            if (!validateCredentials(
                                    email = email,
                                    password = password,
                                    onValidationError = { message = it }
                                )
                            ) {
                                return@launch
                            }
                            isLoading = true
                            val result = authManager.signInWithEmail(email.trim(), password)
                            isLoading = false
                            if (result != null) {
                                onAuthenticated()
                            } else {
                                message = authManager.lastErrorMessage ?: "Invalid email or password"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = IdeColors.AccentBlue)
                ) {
                    Text(if (isLoading) "Logging In..." else "Log In")
                }

                Button(
                    onClick = {
                        scope.launch {
                            if (!validateCredentials(
                                    email = email,
                                    password = password,
                                    onValidationError = { message = it }
                                )
                            ) {
                                return@launch
                            }
                            isLoading = true
                            val result = authManager.signUpWithEmail(email.trim(), password)
                            isLoading = false
                            if (result != null) {
                                onAuthenticated()
                            } else {
                                message = authManager.lastErrorMessage ?: "Sign up failed. Check email/password requirements."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = IdeColors.AccentGreen)
                ) {
                    Text("Sign Up")
                }

                Button(
                    onClick = {
                        message = "Google Sign-In will be enabled after app configuration is complete"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = IdeColors.BgSurface)
                ) {
                    Text("Sign in with Google")
                }

                if (!message.isNullOrBlank()) {
                    Text(
                        text = message.orEmpty(),
                        color = IdeColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedRgbLogo() {
    val infiniteTransition = rememberInfiniteTransition(label = "auth_logo_color")
    val color by infiniteTransition.animateColor(
        initialValue = Color.Red,
        targetValue = Color.Blue,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auth_logo_color_value"
    )

    Text(
        text = "TurnIt",
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = color
    )
}

@Composable
private fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = IdeColors.AccentBlue,
    unfocusedBorderColor = IdeColors.Border,
    focusedTextColor = IdeColors.TextPrimary,
    unfocusedTextColor = IdeColors.TextPrimary,
    focusedLabelColor = IdeColors.AccentBlue,
    unfocusedLabelColor = IdeColors.TextSecondary,
    cursorColor = IdeColors.AccentBlue
)

private inline fun validateCredentials(
    email: String,
    password: String,
    onValidationError: (String) -> Unit
): Boolean {
    return if (email.isBlank() || password.isBlank()) {
        onValidationError("Enter email and password")
        false
    } else {
        true
    }
}
