package com.turnit.ide.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turnit.ide.engine.DownloadState
import com.turnit.ide.engine.ExtractionState

// =======================================================================
// SETUP SCREEN STATE MODEL
// =======================================================================

sealed class SetupPhase {
    object Welcome                               : SetupPhase()
    data class Downloading(val state: DownloadState.Downloading)
                                                 : SetupPhase()
    data class Extracting(val state: ExtractionState.Extracting)
                                                 : SetupPhase()
    object Verifying                             : SetupPhase()
    object Complete                              : SetupPhase()
    data class Error(val message: String)        : SetupPhase()
}

// =======================================================================
// SETUP SCREEN
//
// Displayed on first launch. Guides the user through the toolchain
// payload download + extraction sequence.
//
// Parameters:
//   phase           -> current setup phase (drives UI state)
//   downloadEngine  -> injected to format byte strings
//   onStartSetup    -> triggered by "Download Toolchain" button
//   onRetry         -> triggered by "Retry" on error state
//   onLaunchIde     -> triggered by "Launch IDE" on complete state
// =======================================================================

@Composable
fun SetupScreen(
    phase:        SetupPhase,
    onStartSetup: () -> Unit,
    onRetry:      () -> Unit,
    onLaunchIde:  () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IdeColors.Bg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            IdeLogoBlock()
            when (phase) {
                is SetupPhase.Welcome     -> WelcomePanel(onStart = onStartSetup)
                is SetupPhase.Downloading -> DownloadPanel(phase.state)
                is SetupPhase.Extracting  -> ExtractionPanel(phase.state)
                is SetupPhase.Verifying   -> VerifyingPanel()
                is SetupPhase.Complete    -> CompletePanel(onLaunch = onLaunchIde)
                is SetupPhase.Error       ->
                    ErrorPanel(message = phase.message, onRetry = onRetry)
            }
        }
    }
}

// ---- IDE logo --------------------------------------------------------

@Composable
private fun IdeLogoBlock() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "TurnIt IDE",
            color    = IdeColors.AccentBlue,
            fontSize = 28.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
        Text(
            "Universal Mobile Compiler",
            color    = IdeColors.TextSecondary,
            fontSize = 12.sp
        )
    }
}

// ---- Welcome panel ---------------------------------------------------

@Composable
private fun WelcomePanel(onStart: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        InfoCard(
            lines = listOf(
                "This is a first-run setup.",
                "TurnIt will download a ~600 MB toolchain",
                "payload containing the Ubuntu ARM rootfs,",
                "NDK, JDK 21, SDK, and CMake.",
                "Connect to Wi-Fi before continuing."
            )
        )
        IdeButton(
            text    = "DOWNLOAD TOOLCHAIN",
            onClick = onStart,
            color   = IdeColors.AccentGreen
        )
    }
}

// ---- Download progress panel -----------------------------------------

@Composable
private fun DownloadPanel(state: DownloadState.Downloading) {
    val progress = if (state.totalBytes > 0)
        state.bytesReceived.toFloat() / state.totalBytes.toFloat()
    else 0f

    val animProgress by animateFloatAsState(
        targetValue    = progress,
        animationSpec  = tween(300),
        label          = "dl_progress"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Downloading Toolchain",
            color    = IdeColors.TextPrimary,
            fontSize = 15.sp
        )
        IdeProgressBar(progress = animProgress)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val fmt = com.turnit.ide.engine.DownloadEngine(
                androidx.compose.ui.platform.LocalContext.current
            )
            MonoText("${fmt.formatBytes(state.bytesReceived)} / " +
                     fmt.formatBytes(state.totalBytes))
            MonoText(
                if (state.speedBps > 0)
                    "${fmt.formatBytes(state.speedBps)}/s"
                else "..."
            )
        }
        MonoText("%.1f%%".format(progress * 100))
    }
}

// ---- Extraction panel ------------------------------------------------

@Composable
private fun ExtractionPanel(state: ExtractionState.Extracting) {
    val animProgress by animateFloatAsState(
        targetValue   = state.percentEstimate,
        animationSpec = tween(200),
        label         = "ext_progress"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Extracting Toolchain", color = IdeColors.TextPrimary,
             fontSize = 15.sp)
        IdeProgressBar(progress = animProgress)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MonoText("${state.filesExtracted} files")
            MonoText("%.1f%%".format(state.percentEstimate * 100))
        }
        MonoText(
            state.currentFile,
            color    = IdeColors.TextMuted,
            maxLines = 1
        )
    }
}

// ---- Verifying panel -------------------------------------------------

@Composable
private fun VerifyingPanel() {
    val pulse = rememberInfiniteTransition(label = "verify_pulse")
    val alpha by pulse.animateFloat(
        initialValue  = 0.4f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            tween(700, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "verify_alpha"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IdeProgressBar(progress = -1f)  // indeterminate
        Text(
            "Verifying integrity...",
            color    = IdeColors.AccentCyan.copy(alpha = alpha),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ---- Complete panel --------------------------------------------------

@Composable
private fun CompletePanel(onLaunch: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        InfoCard(
            lines = listOf(
                "Toolchain installed successfully.",
                "PRoot engine ready.",
                "You can now compile C, C++, Java,",
                "Kotlin, and Gradle projects natively."
            ),
            accent = IdeColors.AccentGreen
        )
        IdeButton("LAUNCH IDE", onLaunch, IdeColors.AccentGreen)
    }
}

// ---- Error panel -----------------------------------------------------

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        InfoCard(
            lines  = listOf("Setup failed:", message),
            accent = IdeColors.AccentRed
        )
        IdeButton("RETRY", onRetry, IdeColors.AccentOrange)
    }
}

// ---- Shared components -----------------------------------------------

@Composable
private fun IdeProgressBar(progress: Float) {
    val shape = RoundedCornerShape(4.dp)
    if (progress < 0f) {
        LinearProgressIndicator(
            modifier  = Modifier.fillMaxWidth().height(6.dp).clip(shape),
            color     = IdeColors.AccentBlue,
            trackColor = IdeColors.ProgressTrack,
            strokeCap  = StrokeCap.Round
        )
    } else {
        LinearProgressIndicator(
            progress  = { progress },
            modifier  = Modifier.fillMaxWidth().height(6.dp).clip(shape),
            color     = IdeColors.AccentGreen,
            trackColor = IdeColors.ProgressTrack,
            strokeCap  = StrokeCap.Round
        )
    }
}

@Composable
private fun MonoText(
    text:     String,
    color:    Color   = IdeColors.TextSecondary,
    maxLines: Int     = Int.MAX_VALUE
) {
    Text(
        text       = text,
        color      = color,
        fontSize   = 11.sp,
        fontFamily = FontFamily.Monospace,
        maxLines   = maxLines,
        overflow   = TextOverflow.Ellipsis
    )
}

@Composable
private fun InfoCard(
    lines:  List<String>,
    accent: Color = IdeColors.AccentBlue
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(IdeColors.BgSurface)
            .border(1.dp, accent.copy(alpha = 0.35f),
                    RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        lines.forEach { line ->
            Text(
                text       = line,
                color      = IdeColors.TextSecondary,
                fontSize   = 13.sp,
                textAlign  = TextAlign.Start
            )
        }
    }
}

@Composable
private fun IdeButton(
    text:    String,
    onClick: () -> Unit,
    color:   Color = IdeColors.AccentBlue
) {
    Button(
        onClick  = onClick,
        shape    = RoundedCornerShape(6.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.15f),
            contentColor   = color
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
    ) {
        Text(
            text          = text,
            fontSize      = 12.sp,
            fontFamily    = FontFamily.Monospace,
            letterSpacing = 1.5.sp
        )
    }
}
