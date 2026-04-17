package com.turnit.ide

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.turnit.ide.engine.ExtractionEngine
import com.turnit.ide.ui.IdeColors
import com.turnit.ide.ui.MainShellScreen
import com.turnit.ide.ui.TurnItIdeTheme
import com.turnit.ide.ui.triggerBiometricPrompt
import com.turnit.ide.ui.LoginScreen as AuthScreen
import com.google.firebase.auth.FirebaseAuth

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TurnItIdeTheme {
                MainAppContent()
            }
        }
    }
}

@Composable
private fun MainAppContent() {
    val context = LocalContext.current
    val firebaseAuth = remember { FirebaseAuth.getInstance() }
    var isAuthenticated by remember { mutableStateOf(firebaseAuth.currentUser != null) }
    var isBiometricUnlocked by remember { mutableStateOf(false) }
    var isBootstrapped by remember { mutableStateOf(false) }
    var isBuildRunning by remember { mutableStateOf(false) }
    var biometricError by remember { mutableStateOf<String?>(null) }
    var biometricRetryToken by remember { mutableStateOf(0) }

    LaunchedEffect(firebaseAuth.currentUser?.uid) {
        isAuthenticated = firebaseAuth.currentUser != null
    }

    when {
        !isAuthenticated -> {
            AuthScreen(
                onLoginSuccess = {
                    isAuthenticated = firebaseAuth.currentUser != null
                    isBiometricUnlocked = false
                    isBootstrapped = false
                }
            )
        }

        !isBiometricUnlocked -> {
            val activity = context as? FragmentActivity
            LaunchedEffect(biometricRetryToken) {
                if (activity != null && !isBiometricUnlocked) {
                    triggerBiometricPrompt(
                        activity = activity,
                        onSuccess = {
                            biometricError = null
                            isBiometricUnlocked = true
                        },
                        onError = { error ->
                            biometricError = error
                        }
                    )
                } else if (activity == null) {
                    biometricError = "Unable to launch biometric prompt."
                }
            }

            LaunchedEffect(Unit) {
                isAuthenticated = firebaseAuth.currentUser != null
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(IdeColors.Bg),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Unlocking with biometrics...")
                    biometricError?.let {
                        Text(it, modifier = Modifier.padding(horizontal = 16.dp))
                        Button(onClick = { biometricRetryToken++ }) {
                            Text("Retry biometric unlock")
                        }
                    }
                }
            }
        }

        !isBootstrapped -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Bootstrapping Terminal Engine...")
                }
            }

            LaunchedEffect(Unit) {
                isBootstrapped = ExtractionEngine(context).bootstrapEnvironment(context)
            }
        }

        else -> {
            MainShellScreen(
                isBuildRunning = isBuildRunning,
                onRunBuild = { isBuildRunning = true },
                onStopBuild = { isBuildRunning = false }
            )
        }
    }
}
