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
import com.turnit.ide.auth.FirebaseAuthManager
import com.turnit.ide.ui.AuthScreen
import com.turnit.ide.ui.IdeColors
import com.turnit.ide.ui.MainShellScreen
import com.turnit.ide.ui.TurnItIdeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
private fun MainAppContent() {
    val context = LocalContext.current
    var bootState by remember { mutableStateOf("BOOTING") } // BOOTING, AUTH, BIOMETRIC, READY, ERROR
    var crashLog by remember { mutableStateOf<String?>(null) }
    var authManagerInstance by remember { mutableStateOf<FirebaseAuthManager?>(null) }
    var isBuildRunning by remember { mutableStateOf(false) }
    var bootRetryToken by remember { mutableStateOf(0) }
    var biometricError by remember { mutableStateOf<String?>(null) }
    var biometricRetryToken by remember { mutableStateOf(0) }

    LaunchedEffect(bootRetryToken) {
        androidx.compose.runtime.withFrameNanos { }
        crashLog = null
        biometricError = null
        runCatching {
            val manager = authManagerInstance ?: withContext(Dispatchers.IO) { FirebaseAuthManager() }
            authManagerInstance = manager
            val hasUser = withContext(Dispatchers.IO) { manager.isAuthenticated() }
            bootState = if (hasUser) "BIOMETRIC" else "AUTH"
        }.onFailure { throwable ->
            crashLog = throwable.message ?: "Initialization failed."
            bootState = "ERROR"
        }
    }

    LaunchedEffect(bootState, biometricRetryToken) {
        if (bootState != "BIOMETRIC") {
            return@LaunchedEffect
        }
        val activity = context as? FragmentActivity
        if (activity != null) {
            triggerBiometricPrompt(
                activity = activity,
                onSuccess = {
                    biometricError = null
                    bootState = "READY"
                },
                onError = { error ->
                    biometricError = error
                }
            )
        } else {
            biometricError = "Unable to launch biometric prompt."
        }
    }

    when (bootState) {
        "BOOTING" -> {
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
                    Text("Starting TurnIt IDE...")
                }
            }
        }

        "AUTH" -> {
            val manager = authManagerInstance
            if (manager == null) {
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
                        Text("Authentication initialization is in progress. Retry if this persists.")
                        Button(onClick = {
                            bootState = "BOOTING"
                            bootRetryToken++
                        }) {
                            Text("Retry startup")
                        }
                    }
                }
            } else {
                AuthScreen(
                    authManager = manager,
                    onAuthenticated = {
                        biometricError = null
                        bootState = "BIOMETRIC"
                    }
                )
            }
        }

        "BIOMETRIC" -> {
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

        "READY" -> {
            MainShellScreen(
                isBuildRunning = isBuildRunning,
                onRunBuild = { isBuildRunning = true },
                onStopBuild = { isBuildRunning = false }
            )
        }

        else -> {
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
                    Text(
                        crashLog ?: "An error occurred during app initialization. Please restart the app.",
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Button(onClick = {
                        bootState = "BOOTING"
                        bootRetryToken++
                    }) {
                        Text("Retry startup")
                    }
                }
            }
        }
    }
}
