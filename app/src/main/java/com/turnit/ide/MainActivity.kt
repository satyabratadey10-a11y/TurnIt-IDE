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
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.firebase.auth.FirebaseAuth
import com.turnit.ide.auth.FirebaseAuthManager
import com.turnit.ide.engine.ExtractionEngine
import com.turnit.ide.ui.AuthScreen
import com.turnit.ide.ui.IdeColors
import com.turnit.ide.ui.MainShellScreen
import com.turnit.ide.ui.TurnItIdeTheme

class MainActivity : FragmentActivity() {
    private val authManager by lazy { FirebaseAuthManager() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TurnItIdeTheme {
                MainAppContent(authManager = authManager)
            }
        }
    }
}

fun triggerBiometricPrompt(activity: FragmentActivity, onSuccess: () -> Unit, onError: (String) -> Unit) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { onError(errString.toString()) }
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
        override fun onAuthenticationFailed() { onError("Authentication failed") }
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock TurnIt IDE")
        .setNegativeButtonText("Cancel")
        .build()
    prompt.authenticate(info)
}

@Composable
private fun MainAppContent(authManager: FirebaseAuthManager) {
    val context = LocalContext.current
    val firebaseAuth = remember { FirebaseAuth.getInstance() }
    var isAuthenticated by remember { mutableStateOf(authManager.isAuthenticated()) }
    var isBiometricUnlocked by remember { mutableStateOf(false) }
    var isBootstrapped by remember { mutableStateOf(false) }
    var isBuildRunning by remember { mutableStateOf(false) }
    var biometricError by remember { mutableStateOf<String?>(null) }
    var biometricRetryToken by remember { mutableStateOf(0) }
    var bootstrapError by remember { mutableStateOf<String?>(null) }
    var bootstrapRetryToken by remember { mutableStateOf(0) }

    LaunchedEffect(firebaseAuth.currentUser?.uid) {
        val hasUser = firebaseAuth.currentUser != null
        isAuthenticated = hasUser
        if (!hasUser) {
            isBiometricUnlocked = false
            isBootstrapped = false
            biometricError = null
            bootstrapError = null
        }
    }

    when {
        !isAuthenticated -> {
            AuthScreen(
                authManager = authManager,
                onAuthenticated = {
                    isAuthenticated = authManager.isAuthenticated()
                    isBiometricUnlocked = false
                    isBootstrapped = false
                    biometricError = null
                    bootstrapError = null
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
                modifier = Modifier
                    .fillMaxSize()
                    .background(IdeColors.Bg),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (bootstrapError == null) {
                        CircularProgressIndicator()
                        Text("Bootstrapping Terminal Engine...")
                    } else {
                        Text(bootstrapError.orEmpty(), modifier = Modifier.padding(horizontal = 16.dp))
                        Button(onClick = {
                            bootstrapError = null
                            bootstrapRetryToken++
                        }) {
                            Text("Retry bootstrap")
                        }
                    }
                }
            }

            LaunchedEffect(bootstrapRetryToken) {
                val bootstrapSucceeded = ExtractionEngine(context).bootstrapEnvironment(context)
                isBootstrapped = bootstrapSucceeded
                if (!bootstrapSucceeded) {
                    bootstrapError = "Failed to bootstrap terminal environment."
                }
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
