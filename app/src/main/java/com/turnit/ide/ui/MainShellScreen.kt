package com.turnit.ide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turnit.ide.engine.ShellEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class IdePane { TERMINAL, EDITOR, FILE_TREE }

private data class PaneEntry(
    val pane:  IdePane,
    val label: String,
    val icon:  ImageVector
)

private val PANE_ENTRIES: List<PaneEntry> = listOf(
    PaneEntry(IdePane.TERMINAL,  "Terminal", Icons.Filled.Terminal),
    PaneEntry(IdePane.EDITOR,    "Editor",   Icons.Filled.FolderOpen),
    PaneEntry(IdePane.FILE_TREE, "Files",    Icons.Filled.FolderOpen)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShellScreen(
    onRunBuild:     () -> Unit = {},
    onStopBuild:    () -> Unit = {},
    isBuildRunning: Boolean    = false
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope       = rememberCoroutineScope()
    val context     = LocalContext.current
    var activePane  by remember { mutableStateOf(IdePane.TERMINAL) }

    // Phase 3: Live Terminal State
    val shellEngine = remember { ShellEngine(context) }
    val consoleLogs = remember { mutableStateListOf("TurnIt IDE Shell Engine (v1.0)\n", "Waiting for command...\n") }
    var activeJob   by remember { mutableStateOf<Job?>(null) }
    var isRunning   by remember { mutableStateOf(false) }

    // Hardcoded test command to verify compilers are working in PRoot
    val testCompileCommand = "echo 'Testing Compilers...'; gcc --version; javac -version; pwd; ls -la"

    val handleRunClick = {
        if (!isRunning) {
            isRunning = true
            activePane = IdePane.TERMINAL // Force UI to terminal tab
            consoleLogs.add("\n$ root $testCompileCommand\n")
            activeJob = scope.launch {
                shellEngine.execute(testCompileCommand).collect { outputLine ->
                    consoleLogs.add(outputLine)
                }
                isRunning = false
            }
        }
    }

    val handleStopClick = {
        if (isRunning) {
            activeJob?.cancel()
            consoleLogs.add("\n[Process Killed by User]\n")
            isRunning = false
        }
    }

    ModalNavigationDrawer(
        drawerState   = drawerState,
        drawerContent = {
            IdeDrawerContent(
                activePane   = activePane,
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
                    onRun          = handleRunClick,
                    onStop         = handleStopClick,
                    isBuildRunning = isRunning
                )
            }
        ) { pad ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
            ) {
                when (activePane) {
                    IdePane.TERMINAL  -> TerminalConsoleView(consoleLogs)
                    IdePane.EDITOR    -> CodeEditorView()
                    IdePane.FILE_TREE -> FileTreePanePlaceholder()
                }
            }
        }
    }
}

@Composable
private fun TerminalConsoleView(logs: List<String>) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new logs
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(IdeColors.Bg)
            .padding(8.dp)
    ) {
        items(logs) { line ->
            Text(
                text = line,
                color = IdeColors.TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun CodeEditorView() {
    var text by remember { mutableStateOf("fun main() {\n    println(\"Hello TurnIt!\")\n}") }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(IdeColors.Bg)
    ) {
        // --- LEFT GUTTER: Line Numbers ---
        Box(
            modifier = Modifier
                .background(IdeColors.BgSurface)
                .fillMaxHeight()
                .width(48.dp)
                .padding(8.dp),
            contentAlignment = Alignment.TopStart
        ) {
            LazyColumn {
                items(30) { lineNumber ->
                    Text(
                        text = (lineNumber + 1).toString(),
                        color = IdeColors.TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }

        // --- RIGHT SIDE: Code Editor ---
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(8.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                color = IdeColors.TextPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp
            )
        )
    }
}

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
                    color         = IdeColors.AccentBlue,
                    fontSize      = 16.sp,
                    fontFamily    = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            },
            navigationIcon = {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector        = Icons.Filled.Menu,
                        contentDescription = "Menu",
                        tint               = IdeColors.TextSecondary
                    )
                }
            },
            actions = {
                if (isBuildRunning) {
                    IconButton(onClick = onStop) {
                        Icon(
                            imageVector        = Icons.Filled.Stop,
                            contentDescription = "Stop",
                            tint               = IdeColors.AccentRed
                        )
                    }
                } else {
                    IconButton(onClick = onRun) {
                        Icon(
                            imageVector        = Icons.Filled.PlayArrow,
                            contentDescription = "Run",
                            tint               = IdeColors.AccentGreen
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = IdeColors.BgSurface
            )
        )
        PaneTabStrip(activePane = activePane, onSelect = onPaneSelect)
        HorizontalDivider(color = IdeColors.Border, thickness = 1.dp)
    }
}

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
        val tabs: List<Pair<IdePane, String>> = listOf(
            IdePane.TERMINAL  to "TERMINAL",
            IdePane.EDITOR    to "EDITOR",
            IdePane.FILE_TREE to "FILES"
        )
        tabs.forEach { (pane, label) ->
            PaneTab(
                label    = label,
                isActive = pane == activePane,
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
    val textColor = if (isActive) IdeColors.AccentBlue else IdeColors.TextMuted

    Box(
        modifier = Modifier
            .height(26.dp)
            .background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, border, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text          = label,
            color         = textColor,
            fontSize      = 10.sp,
            fontFamily    = FontFamily.Monospace,
            letterSpacing = 0.8.sp
        )
    }
}

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

        PANE_ENTRIES.forEach { entry ->
            NavigationDrawerItem(
                icon     = {
                    Icon(
                        imageVector        = entry.icon,
                        contentDescription = entry.label
                    )
                },
                label    = { Text(entry.label, fontSize = 13.sp) },
                selected = entry.pane == activePane,
                onClick  = { onSelectPane(entry.pane) },
                colors   = itemColors,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

@Composable
private fun FileTreePanePlaceholder() =
    PlaceholderPane("[files] Phase 4: FileTreePanel", IdeColors.AccentPurple)

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
