package com.turnit.ide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// =======================================================================
// IDE PANE DEFINITION
// Extensible enum for the main content tabs.
// =======================================================================

enum class IdePane { TERMINAL, EDITOR, FILE_TREE }

// =======================================================================
// MAIN SHELL SCREEN
//
// Phase 1 scaffold. Pane bodies are placeholder boxes.
// Phase 3/4 will replace them with TerminalConsoleView + CodeEditorView.
//
// Layout:
//   TopAppBar  -> menu + pane tabs + run/stop actions
//   DrawerSheet -> file tree (will host FileTreePanel in Phase 4)
//   Content     -> active pane content
// =======================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShellScreen(
    onRunBuild: () -> Unit = {},
    onStopBuild: () -> Unit = {},
    isBuildRunning: Boolean = false
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope       = rememberCoroutineScope()
    var activePane  by remember { mutableStateOf(IdePane.TERMINAL) }

    ModalNavigationDrawer(
        drawerState   = drawerState,
        drawerContent = {
            IdeDrawerContent(
                activePane = activePane,
                onSelectPane = { pane ->
                    activePane = pane
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            containerColor = IdeColors.Bg,
            topBar = {
                IdeTopBar(
                    activePane     = activePane,
                    onMenuClick    = { scope.launch { drawerState.open() } },
                    onPaneSelect   = { activePane = it },
                    onRun          = onRunBuild,
                    onStop         = onStopBuild,
                    isBuildRunning = isBuildRunning
                )
            }
        ) { pad ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
            ) {
                when (activePane) {
                    IdePane.TERMINAL  -> TerminalPanePlaceholder()
                    IdePane.EDITOR    -> EditorPanePlaceholder()
                    IdePane.FILE_TREE -> FileTreePanePlaceholder()
                }
            }
        }
    }
}

// ---- Top app bar -------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdeTopBar(
    activePane:     IdePane,
    onMenuClick:    () -> Unit,
    onPaneSelect:   (IdePane) -> Unit,
    onRun:          () -> Unit,
    onStop:         () -> Unit,
    isBuildRunning: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(IdeColors.BgSurface)
    ) {
        TopAppBar(
            title = {
                Text(
                    "TurnIt IDE",
                    color      = IdeColors.AccentBlue,
                    fontSize   = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            },
            navigationIcon = {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Filled.Menu, "Menu",
                         tint = IdeColors.TextSecondary)
                }
            },
            actions = {
                // Run / Stop toggle
                if (isBuildRunning) {
                    IconButton(onClick = onStop) {
                        Icon(Icons.Filled.Stop, "Stop",
                             tint = IdeColors.AccentRed)
                    }
                } else {
                    IconButton(onClick = onRun) {
                        Icon(Icons.Filled.PlayArrow, "Run",
                             tint = IdeColors.AccentGreen)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = IdeColors.BgSurface
            )
        )
        // Pane tab strip
        PaneTabStrip(activePane = activePane, onSelect = onPaneSelect)
        HorizontalDivider(
            color     = IdeColors.Border,
            thickness = 1.dp
        )
    }
}

// ---- Pane tab strip ----------------------------------------------------

@Composable
private fun PaneTabStrip(
    activePane: IdePane,
    onSelect:   (IdePane) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(IdeColors.BgSurface)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            IdePane.TERMINAL  to "TERMINAL",
            IdePane.EDITOR    to "EDITOR",
            IdePane.FILE_TREE to "FILES"
        ).forEach { (pane, label) ->
            val isActive = pane == activePane
            PaneTab(
                label    = label,
                isActive = isActive,
                onClick  = { onSelect(pane) }
            )
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun PaneTab(
    label:    String,
    isActive: Boolean,
    onClick:  () -> Unit
) {
    val bg     = if (isActive) IdeColors.AccentBlue.copy(alpha = 0.12f)
                 else Color.Transparent
    val border = if (isActive) IdeColors.AccentBlue.copy(alpha = 0.60f)
                 else IdeColors.Border
    val text   = if (isActive) IdeColors.AccentBlue else IdeColors.TextMuted

    Box(
        modifier = Modifier
            .height(26.dp)
            .background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, border, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text          = label,
            color         = text,
            fontSize      = 10.sp,
            fontFamily    = FontFamily.Monospace,
            letterSpacing = 0.8.sp
        )
        // Clickable handled via foundation click on the Box
        // Using androidx.compose.foundation.clickable would require
        // ripple import; use a Modifier.clickable directly.
        androidx.compose.foundation.clickable.also {
            // intentional no-op reference to avoid missing import
        }
    }
}

// ---- Navigation drawer ------------------------------------------------

@Composable
private fun IdeDrawerContent(
    activePane:   IdePane,
    onSelectPane: (IdePane) -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = IdeColors.BgSurface,
        drawerContentColor   = IdeColors.TextPrimary
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "TurnIt IDE",
                color      = IdeColors.AccentBlue,
                fontSize   = 14.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "Universal Compiler",
                color    = IdeColors.TextMuted,
                fontSize = 11.sp
            )
        }
        HorizontalDivider(color = IdeColors.Border)
        Spacer(Modifier.height(8.dp))
        val itemColors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor   = IdeColors.AccentBlue.copy(alpha = 0.12f),
            unselectedContainerColor = Color.Transparent,
            selectedTextColor        = IdeColors.AccentBlue,
            unselectedTextColor      = IdeColors.TextSecondary,
            selectedIconColor        = IdeColors.AccentBlue,
            unselectedIconColor      = IdeColors.TextMuted
        )
        listOf(
            Triple(IdePane.TERMINAL,  "Terminal",  Icons.Filled.Terminal),
            Triple(IdePane.EDITOR,    "Editor",    Icons.Filled.FolderOpen),
            Triple(IdePane.FILE_TREE, "Files",     Icons.Filled.FolderOpen)
        ).forEach { (pane, label, icon) ->
            NavigationDrawerItem(
                icon     = { Icon(icon, label) },
                label    = { Text(label, fontSize = 13.sp) },
                selected = pane == activePane,
                onClick  = { onSelectPane(pane) },
                colors   = itemColors,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

// ---- Pane placeholder bodies (replaced in Phase 3/4) ------------------

@Composable
private fun TerminalPanePlaceholder() {
    PlaceholderPane(
        label   = "[terminal] Phase 3: TerminalConsoleView",
        accent  = IdeColors.AccentGreen
    )
}

@Composable
private fun EditorPanePlaceholder() {
    PlaceholderPane(
        label  = "[editor] Phase 4: CodeEditorView",
        accent = IdeColors.AccentBlue
    )
}

@Composable
private fun FileTreePanePlaceholder() {
    PlaceholderPane(
        label  = "[files] Phase 4: FileTreePanel",
        accent = IdeColors.AccentPurple
    )
}

@Composable
private fun PlaceholderPane(label: String, accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IdeColors.Bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = label,
            color      = accent.copy(alpha = 0.4f),
            fontSize   = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
