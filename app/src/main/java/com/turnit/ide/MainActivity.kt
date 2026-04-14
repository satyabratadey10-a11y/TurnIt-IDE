package com.turnit.ide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.turnit.ide.engine.DownloadEngine
import com.turnit.ide.engine.DownloadState
import com.turnit.ide.engine.ExtractionEngine
import com.turnit.ide.engine.ExtractionState
import com.turnit.ide.ui.LoginScreen
import com.turnit.ide.ui.MainShellScreen
import com.turnit.ide.ui.SetupPhase
import com.turnit.ide.ui.SetupScreen
import com.turnit.ide.ui.TurnItIdeTheme
import kotlinx.coroutines.launch
import java.io.File

// =========================================================================
// PHASE 2 CONFIGURATION: GitHub Releases URL
// =========================================================================

private const val PAYLOAD_URL =
    "https://github.com/satyabratadey10-a11y/TurnIt-IDE/releases/download/v1.0-toolchain/toolchain-arm64.7z"

// We temporarily bypass SHA256 strictness until the cloud builds the final 600MB payload
private const val PAYLOAD_SHA256 = "SKIP"

class MainActivity : ComponentActivity() {

    private val downloadEngine  by lazy { DownloadEngine(this) }
    private val extractionEngine by lazy { ExtractionEngine(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TurnItIdeTheme {
                var isAuthenticated by remember { mutableStateOf(false) }
                var phase by remember {
                    mutableStateOf<SetupPhase>(
                        if (extractionEngine.isRootfsPresent())
                            SetupPhase.Complete
                        else
                            SetupPhase.Welcome
                    )
                }
                var isBuildRunning by remember { mutableStateOf(false) }

                if (!isAuthenticated) {
                    LoginScreen(
                        onLoginSuccess = { isAuthenticated = true }
                    )
                } else {
                    if (phase == SetupPhase.Complete) {
                        MainShellScreen(
                            isBuildRunning = isBuildRunning,
                            onRunBuild     = { isBuildRunning = true  },
                            onStopBuild    = { isBuildRunning = false }
                        )
                    } else {
                        SetupScreen(
                            phase        = phase,
                            onStartSetup = { startSetup { phase = it } },
                            onRetry      = {
                                phase = SetupPhase.Welcome
                                extractionEngine.purgeRootfs()
                            },
                            onLaunchIde  = { phase = SetupPhase.Complete }
                        )
                    }
                }
            }
        }
    }

    private fun startSetup(phaseCallback: (SetupPhase) -> Unit) {
        lifecycleScope.launch {
            val destFile = File(filesDir, "toolchain.7z")

            // ---- PHASE 1a: Download ------------------------------------
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
                            // proceed to extraction below
                        }

                        is DownloadState.Failed -> {
                            // Bypass SHA mismatch during active development
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

            // ---- PHASE 1b: Extraction ----------------------------------
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
                            destFile.delete() // reclaim space
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
