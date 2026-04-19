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
import com.turnit.ide.engine.DownloadEngine
import com.turnit.ide.engine.DownloadState
import com.turnit.ide.engine.ExtractionEngine
import com.turnit.ide.ui.AuthScreen
import com.turnit.ide.ui.IdeColors
import com.turnit.ide.ui.MainShellScreen
import com.turnit.ide.ui.TurnItIdeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private const val PAYLOAD_URL =
    "https://github.com/satyabratadey10-a11y/TurnIt-IDE/releases/download/v1.0-toolchain/toolchain-arm64.7z"
private const val PAYLOAD_SHA256 =
    "c62a9278d51a9f7112d9da80b29ab28d97b8dc00c11b98aece152923c677837c"
private const val PAYLOAD_FILENAME = "toolchain.7z"

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
    var bootState by remember { mutableStateOf("BOOTING") } // BOOTING, AUTH, BIOMETRIC, DOWNLOADING, EXTRACTION, READY, ERROR
    var crashLog by remember { mutableStateOf<String?>(null) }
    var authManagerInstance by remember { mutableStateOf<FirebaseAuthManager?>(null) }
    var isBuildRunning by remember { mutableStateOf(false) }
    var bootRetryToken by remember { mutableStateOf(0) }
    var biometricError by remember { mutableStateOf<String?>(null) }
    var biometricRetryToken by remember { mutableStateOf(0) }
    var downloadStatusText by remember { mutableStateOf("Starting payload download...") }
    val downloadEngine = remember(context) { DownloadEngine(context) }

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
                    bootState = "DOWNLOADING"
                },
                onError = { error ->
                    biometricError = error
                }
            )
        } else {
            biometricError = "Unable to launch biometric prompt."
        }
    }

    LaunchedEffect(bootState) {
        if (bootState != "DOWNLOADING") {
            return@LaunchedEffect
        }
        val destFile = File(context.filesDir, PAYLOAD_FILENAME)
        if (destFile.exists()) {
            bootState = "EXTRACTION"
            return@LaunchedEffect
        }
        downloadEngine.download(
            url = PAYLOAD_URL,
            destFile = destFile,
            sha256 = PAYLOAD_SHA256
        ).collect { state ->
            when (state) {
                is DownloadState.Connecting -> {
                    downloadStatusText = "Connecting to payload server..."
                }
                is DownloadState.Downloading -> {
                    val totalBytes = state.totalBytes
                    val progressLabel = if (totalBytes > 0) {
                        val progress = (state.bytesReceived * 100.0 / totalBytes)
                            .coerceIn(0.0, 100.0)
                        String.format(Locale.US, "%.1f%%", progress)
                    } else {
                        "..."
                    }
                    val totalLabel = if (totalBytes > 0) {
                        downloadEngine.formatBytes(totalBytes)
                    } else {
                        "unknown size"
                    }
                    downloadStatusText =
                        "Downloading payload $progressLabel (${downloadEngine.formatBytes(state.bytesReceived)} / $totalLabel)"
                }
                is DownloadState.Verifying -> {
                    downloadStatusText = "Verifying payload integrity..."
                }
                is DownloadState.Done -> {
                    bootState = "EXTRACTION"
                }
                is DownloadState.Failed -> {
                    val causeDetails = state.cause?.message
                        ?: state.cause?.let { it::class.simpleName }
                        ?: "Unknown error"
                    crashLog = "${state.reason}: $causeDetails"
                    bootState = "ERROR"
                }
                DownloadState.Idle -> {
                    downloadStatusText = "Starting payload download..."
                }
            }
        }
    }

    LaunchedEffect(bootState) {
        if (bootState != "EXTRACTION") {
            return@LaunchedEffect
        }
        val bootstrapSucceeded = withContext(Dispatchers.IO) {
            ExtractionEngine(context).bootstrapEnvironment(context)
        }
        if (bootstrapSucceeded) {
            bootState = "READY"
        } else {
            crashLog = "Terminal bootstrap failed during initialization."
            bootState = "ERROR"
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

        "DOWNLOADING" -> {
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
                    Text(downloadStatusText, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }

        "EXTRACTION" -> {
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
                    Text("Bootstrapping Terminal Engine...")
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
