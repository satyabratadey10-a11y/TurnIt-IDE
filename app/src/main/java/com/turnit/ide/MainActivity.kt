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
import com.turnit.ide.ui.MainShellScreen
import com.turnit.ide.ui.SetupPhase
import com.turnit.ide.ui.SetupScreen
import com.turnit.ide.ui.TurnItIdeTheme
import kotlinx.coroutines.launch
import java.io.File

// =========================================================================
// CONFIGURATION - update before first build
// =========================================================================

private const val PAYLOAD_URL    =
    "https://your-cdn.example.com/toolchain-arm64.7z"
private const val PAYLOAD_SHA256 =
    "0000000000000000000000000000000000000000000000000000000000000000"

class MainActivity : ComponentActivity() {

    private val downloadEngine  by lazy { DownloadEngine(this) }
    private val extractionEngine by lazy { ExtractionEngine(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TurnItIdeTheme {
                // ----------------------------------------------------------
                // Top-level state hoisted here so lifecycleScope.launch
                // can update it from coroutines outside the composition.
                // mutableStateOf is read by Compose because it is
                // delegated with `by` and observed in the Composable tree.
                // ----------------------------------------------------------
                var phase by remember {
                    mutableStateOf<SetupPhase>(
                        if (extractionEngine.isRootfsPresent())
                            SetupPhase.Complete
                        else
                            SetupPhase.Welcome
                    )
                }
                var isBuildRunning by remember { mutableStateOf(false) }

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

    // ---- Setup pipeline ------------------------------------------------
    //
    // Runs download -> extraction on the lifecycleScope so it survives
    // configuration changes. The phaseCallback lambda is called on the
    // main thread (Flow is collected on Default, but the lambda is posted
    // to main via the coroutine context of lifecycleScope).

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
                            phaseCallback(SetupPhase.Error(
                                "Download: ${state.reason}"
                            ))
                            return@collect
                        }
                    }
                }

            // Abort if download ended in error
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
                            destFile.delete() // reclaim ~600 MB
                            phaseCallback(SetupPhase.Complete)
                        }

                        is ExtractionState.Failed -> {
                            phaseCallback(SetupPhase.Error(
                                "Extraction: ${state.reason}"
                            ))
                        }
                    }
                }
        }
    }
}
