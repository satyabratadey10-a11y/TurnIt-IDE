package com.turnit.ide.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.turnit.ide.R
import com.turnit.ide.auth.FirebaseAuthManager
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    authManager: FirebaseAuthManager,
    onAuthenticated: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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
                    .paint(
                        painter = painterResource(id = R.drawable.bg_glass_bubble),
                        contentScale = androidx.compose.ui.layout.ContentScale.FillBounds
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
                            if (email.isBlank() || password.isBlank()) {
                                message = "Enter email and password"
                                return@launch
                            }
                            isLoading = true
                            val result = authManager.signInWithEmail(email.trim(), password)
                            isLoading = false
                            if (result != null) {
                                onAuthenticated()
                            } else {
                                message = "Login failed"
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
                            if (email.isBlank() || password.isBlank()) {
                                message = "Enter email and password"
                                return@launch
                            }
                            isLoading = true
                            val result = authManager.signUpWithEmail(email.trim(), password)
                            isLoading = false
                            if (result != null) {
                                onAuthenticated()
                            } else {
                                message = "Sign up failed"
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
                        authManager.buildGoogleSignInClient(context = context, webClientId = null)
                        message = "Google Sign-In launcher setup placeholder ready"
                    },
                    modifier = Modifier.fillMaxWidth(),
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
    val transition = rememberInfiniteTransition(label = "auth_logo_shift")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auth_logo_shift_value"
    )

    Text(
        text = "TurnIt",
        style = TextStyle(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFF3B3B),
                    Color(0xFF3BFF4F),
                    Color(0xFF3B82FF),
                    Color(0xFFFF3B3B)
                ),
                start = Offset(shift - 300f, 0f),
                end = Offset(shift + 300f, 0f)
            ),
            fontSize = 42.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
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
