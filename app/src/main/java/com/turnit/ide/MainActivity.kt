package com.turnit.ide

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.turnit.ide.auth.FirebaseAuthManager
import com.turnit.ide.engine.DownloadEngine
import com.turnit.ide.engine.DownloadState
import com.turnit.ide.engine.ExtractionEngine
import com.turnit.ide.engine.ExtractionState
import com.turnit.ide.ui.AuthScreen
import com.turnit.ide.ui.IdeColors
import com.turnit.ide.ui.MainShellScreen
import com.turnit.ide.ui.SetupPhase
import com.turnit.ide.ui.SetupScreen
import com.turnit.ide.ui.TurnItIdeTheme
import com.turnit.ide.ui.triggerBiometricPrompt
import kotlinx.coroutines.launch
import java.io.File

private const val PAYLOAD_URL =
    "https://github.com/satyabratadey10-a11y/TurnIt-IDE/releases/download/v1.0-toolchain/toolchain-arm64.7z"

private const val PAYLOAD_SHA256 = "SKIP"

class MainActivity : FragmentActivity() {

    private val downloadEngine by lazy { DownloadEngine(this) }
    private val extractionEngine by lazy { ExtractionEngine(this) }
    private val authManager by lazy {
        FirebaseAuthManager(
            firebaseAuth = FirebaseAuth.getInstance(),
            firestore = FirebaseFirestore.getInstance()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            ComposeView(this).apply {
                setContent {
                    TurnItIdeTheme {
                        var isAuthenticated by remember {
                            mutableStateOf(authManager.isAuthenticated())
                        }
                        var isBiometricUnlocked by remember { mutableStateOf(false) }
                        var biometricError by remember { mutableStateOf<String?>(null) }
                        var biometricRequested by remember { mutableStateOf(false) }
                        var phase by remember {
                            mutableStateOf<SetupPhase>(
                                if (extractionEngine.isRootfsPresent()) SetupPhase.Complete
                                else SetupPhase.Welcome
                            )
                        }
                        var isBuildRunning by remember { mutableStateOf(false) }

                        DisposableEffect(Unit) {
                            val listener = FirebaseAuth.AuthStateListener { auth ->
                                val hasUser = auth.currentUser != null
                                isAuthenticated = hasUser
                                if (!hasUser) {
                                    isBiometricUnlocked = false
                                    biometricRequested = false
                                    biometricError = null
                                }
                            }
                            FirebaseAuth.getInstance().addAuthStateListener(listener)
                            onDispose {
                                FirebaseAuth.getInstance().removeAuthStateListener(listener)
                            }
                        }

                        if (!isAuthenticated) {
                            AuthScreen(
                                authManager = authManager,
                                onAuthenticated = {
                                    isAuthenticated = true
                                    isBiometricUnlocked = false
                                    biometricError = null
                                    biometricRequested = false
                                }
                            )
                        } else if (!isBiometricUnlocked) {
                            LaunchedEffect(isAuthenticated, biometricRequested) {
                                if (!biometricRequested) {
                                    biometricRequested = true
                                    triggerBiometricPrompt(
                                        activity = this@MainActivity,
                                        onSuccess = {
                                            isBiometricUnlocked = true
                                            biometricError = null
                                        },
                                        onError = { errorMessage ->
                                            biometricError = errorMessage
                                            biometricRequested = false
                                        }
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(IdeColors.Bg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = biometricError
                                        ?: "Authenticate with biometrics to unlock TurnIt IDE",
                                    color = IdeColors.TextSecondary
                                )
                            }
                        } else {
                            if (phase == SetupPhase.Complete) {
                                MainShellScreen(
                                    isBuildRunning = isBuildRunning,
                                    onRunBuild = { isBuildRunning = true },
                                    onStopBuild = { isBuildRunning = false }
                                )
                            } else {
                                SetupScreen(
                                    phase = phase,
                                    onStartSetup = { startSetup { phase = it } },
                                    onRetry = {
                                        phase = SetupPhase.Welcome
                                        extractionEngine.purgeRootfs()
                                    },
                                    onLaunchIde = { phase = SetupPhase.Complete }
                                )
                            }
                        }
                    }
                }
            }
        )
    }

    private fun startSetup(phaseCallback: (SetupPhase) -> Unit) {
        lifecycleScope.launch {
            val destFile = File(filesDir, "toolchain.7z")

            downloadEngine
                .download(PAYLOAD_URL, destFile, PAYLOAD_SHA256)
                .collect { state ->
                    when (state) {
                        is DownloadState.Idle,
                        is DownloadState.Connecting ->
                            phaseCallback(SetupPhase.Welcome)

                        is DownloadState.Downloading ->
                            phaseCallback(SetupPhase.Downloading(state))

                        is DownloadState.Verifying ->
                            phaseCallback(SetupPhase.Verifying)

                        is DownloadState.Done -> {
                        }

                        is DownloadState.Failed -> {
                            if (state.reason.contains("SHA-256")) {
                                phaseCallback(SetupPhase.Verifying)
                            } else {
                                phaseCallback(SetupPhase.Error("Download: ${state.reason}"))
                                return@collect
                            }
                        }
                    }
                }

            if (!destFile.exists()) return@launch

            extractionEngine
                .extract(destFile)
                .collect { state ->
                    when (state) {
                        is ExtractionState.Idle,
                        is ExtractionState.Preparing ->
                            phaseCallback(SetupPhase.Verifying)

                        is ExtractionState.Extracting ->
                            phaseCallback(SetupPhase.Extracting(state))

                        is ExtractionState.Done -> {
                            destFile.delete()
                            phaseCallback(SetupPhase.Complete)
                        }

                        is ExtractionState.Failed -> {
                            phaseCallback(SetupPhase.Error("Extraction: ${state.reason}"))
                        }
                    }
                }
        }
    }
}
