package com.turnit.ide.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turnit.ide.engine.ShellEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class IdePane { TERMINAL, EDITOR, FILE_TREE }

private data class ChatMessage(val text: String, val fromUser: Boolean)
data class AiModel(val name: String, val apiUrl: String, val apiKey: String, val isCustom: Boolean = false)
private const val CHAT_PLACEHOLDER_TEXT = "Type your message..."
private val SPLITTER_HANDLE_COLOR = Color(0x88999999)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShellScreen(
    onRunBuild: () -> Unit = {},
    onStopBuild: () -> Unit = {},
    isBuildRunning: Boolean = false
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scaffoldState = rememberBottomSheetScaffoldState()

    var activePane by remember { mutableStateOf(IdePane.TERMINAL) }
    var leftPaneWeight by remember { mutableFloatStateOf(0.5f) }

    val shellEngine = remember { ShellEngine(context) }
    val consoleLogs = remember {
        mutableStateListOf(
            "TurnIt IDE Shell Engine (v1.0)\n",
            "Waiting for command...\n"
        )
    }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var isRunning by remember { mutableStateOf(false) }

    val testCompileCommand = "echo 'Testing Compilers...'; gcc --version; javac -version; pwd; ls -la"

    val handleRunClick = {
        if (!isRunning) {
            isRunning = true
            activePane = IdePane.TERMINAL
            consoleLogs.add("\n$ root $testCompileCommand\n")
            onRunBuild()
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
            onStopBuild()
        }
    }

    val addCustomModelOption = remember {
        AiModel(
            name = "+ Add Custom Model",
            apiUrl = "",
            apiKey = ""
        )
    }
    val modelOptions = remember {
        mutableStateListOf(
            AiModel("Gemini 3 Flash", "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash:generateContent", ""),
            AiModel("Gemini 2.5 Fast", "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent", ""),
            AiModel("Qwen 3.5", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", ""),
            addCustomModelOption
        )
    }
    var selectedModel by remember { mutableStateOf(modelOptions.first()) }
    var showCustomModelDialog by remember { mutableStateOf(false) }
    var customModelName by remember { mutableStateOf("") }
    var customModelUrl by remember { mutableStateOf("") }
    var customModelApiKey by remember { mutableStateOf("") }
    val clearCustomModelInputs = {
        customModelName = ""
        customModelUrl = ""
        customModelApiKey = ""
    }
    val isCustomModelUrlValid = remember(customModelUrl) {
        val trimmedUrl = customModelUrl.trim()
        trimmedUrl.startsWith("https://") || trimmedUrl.startsWith("http://")
    }
    val isCustomModelInputValid = customModelName.isNotBlank() && isCustomModelUrlValid
    val chatMessages = remember {
        mutableStateListOf(
            ChatMessage("Welcome to TurnIt AI assistant.", false)
        )
    }
    var chatInput by remember { mutableStateOf("") }
    val appendMockModelResponse = {
        chatMessages.add(ChatMessage("Model [${selectedModel.name}] is processing your request.", false))
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = IdeColors.BgSurface,
                drawerContentColor = IdeColors.TextPrimary
            ) {
                Spacer(Modifier.height(12.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.PostAdd, contentDescription = "New Chat") },
                    label = { Text("New Chat") },
                    selected = false,
                    onClick = {
                        chatMessages.clear()
                        chatMessages.add(ChatMessage("New chat started.", false))
                        chatInput = ""
                        scope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = IdeColors.TextSecondary,
                        unselectedIconColor = IdeColors.TextMuted
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.History, contentDescription = "History") },
                    label = { Text("History") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = IdeColors.TextSecondary,
                        unselectedIconColor = IdeColors.TextMuted
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Key, contentDescription = "API Key Settings") },
                    label = { Text("API Key Settings") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = IdeColors.TextSecondary,
                        unselectedIconColor = IdeColors.TextMuted
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            containerColor = IdeColors.Bg,
            sheetContainerColor = IdeColors.BgSurface,
            sheetPeekHeight = 56.dp,
            topBar = {
                val rainbowShift = rememberInfiniteTransition(label = "brand_shift")
                val shift by rainbowShift.animateFloat(
                    initialValue = 0f,
                    targetValue = 1000f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(4000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "brand_shift_anim"
                )
                TopAppBar(
                    title = {
                        Text(
                            text = "TurnIt",
                            style = TextStyle(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFFF3B3B),
                                        Color(0xFF3BFF4F),
                                        Color(0xFF3B82FF),
                                        Color(0xFFFF3B3B)
                                    ),
                                    start = Offset(shift - 300f, 0f),
                                    end = Offset(shift + 300f, 0f)
                                ),
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "Menu",
                                tint = IdeColors.TextSecondary
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (isRunning || isBuildRunning) handleStopClick() else handleRunClick()
                        }) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                tint = if (isRunning || isBuildRunning) IdeColors.AccentOrange else IdeColors.AccentGreen
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = IdeColors.BgSurface
                    )
                )
            },
            sheetContent = {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .background(IdeColors.BgSurface)
                ) {
                    if (maxWidth < 900.dp) {
                        ChatPane(
                            selectedModel = selectedModel,
                            modelOptions = modelOptions,
                            onModelSelected = { model ->
                                if (model == addCustomModelOption) {
                                    clearCustomModelInputs()
                                    showCustomModelDialog = true
                                } else {
                                    selectedModel = model
                                }
                            },
                            messages = chatMessages,
                            input = chatInput,
                            onInputChange = { chatInput = it },
                            onSend = {
                                if (chatInput.isNotBlank()) {
                                    chatMessages.add(ChatMessage(chatInput.trim(), true))
                                    appendMockModelResponse()
                                    chatInput = ""
                                }
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(IdeColors.BgSurface)
                        )
                    }
                }
            }
        ) { pad ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
            ) {
                val totalWidthInPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val useBottomSheetForChat = maxWidth < 900.dp
                val dividerX = maxWidth * leftPaneWeight
                val handleOffsetX = dividerX - 12.dp

                Box(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(leftPaneWeight)
                                .fillMaxHeight()
                                .background(IdeColors.Bg)
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                PaneTabStrip(
                                    activePane = activePane,
                                    onSelect = { activePane = it }
                                )
                                HorizontalDivider(color = IdeColors.Border, thickness = 1.dp)
                                Box(modifier = Modifier.fillMaxSize()) {
                                    when (activePane) {
                                        IdePane.TERMINAL -> TerminalConsoleView(consoleLogs)
                                        IdePane.EDITOR -> CodeEditorView()
                                        IdePane.FILE_TREE -> FileTreePanePlaceholder()
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f - leftPaneWeight)
                                .fillMaxHeight()
                                .background(IdeColors.BgSurface)
                        ) {
                            if (useBottomSheetForChat) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "AI Chat is in bottom sheet",
                                        color = IdeColors.TextMuted,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                ChatPane(
                                    selectedModel = selectedModel,
                                    modelOptions = modelOptions,
                                    onModelSelected = { model ->
                                        if (model == addCustomModelOption) {
                                            clearCustomModelInputs()
                                            showCustomModelDialog = true
                                        } else {
                                            selectedModel = model
                                        }
                                    },
                                    messages = chatMessages,
                                    input = chatInput,
                                    onInputChange = { chatInput = it },
                                    onSend = {
                                        if (chatInput.isNotBlank()) {
                                            chatMessages.add(ChatMessage(chatInput.trim(), true))
                                            appendMockModelResponse()
                                            chatInput = ""
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = handleOffsetX)
                            .size(width = 24.dp, height = 72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SPLITTER_HANDLE_COLOR)
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { _, dragAmount ->
                                    val deltaWeight = dragAmount / totalWidthInPx
                                    leftPaneWeight =
                                        (leftPaneWeight + deltaWeight).coerceIn(0.2f, 0.8f)
                                }
                            }
                    )
                }
            }
        }
    }

    if (showCustomModelDialog) {
        AlertDialog(
            onDismissRequest = {
                showCustomModelDialog = false
                clearCustomModelInputs()
            },
            title = { Text("Add Custom Model") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customModelName,
                        onValueChange = { customModelName = it },
                        label = { Text("Model Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = customModelUrl,
                        onValueChange = { customModelUrl = it },
                        label = { Text("API Provider URL") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = customModelApiKey,
                        onValueChange = { customModelApiKey = it },
                        label = { Text("API Key (Optional)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!isCustomModelInputValid) return@Button
                        val newModel = AiModel(
                            name = customModelName.trim(),
                            apiUrl = customModelUrl.trim(),
                            apiKey = customModelApiKey.trim(),
                            isCustom = true
                        )
                        val addOptionIndex = modelOptions.indexOf(addCustomModelOption)
                        if (addOptionIndex >= 0) {
                            modelOptions.add(addOptionIndex, newModel)
                        } else {
                            modelOptions.add(newModel)
                        }
                        selectedModel = newModel
                        showCustomModelDialog = false
                        clearCustomModelInputs()
                    },
                    enabled = isCustomModelInputValid
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCustomModelDialog = false
                        clearCustomModelInputs()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ChatPane(
    selectedModel: AiModel,
    modelOptions: List<AiModel>,
    onModelSelected: (AiModel) -> Unit,
    messages: List<ChatMessage>,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    var modelMenuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IdeColors.BgSurface)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(IdeColors.Bg)
                .border(1.dp, IdeColors.Border, RoundedCornerShape(10.dp))
                .clickable { modelMenuOpen = true }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = selectedModel.name,
                color = IdeColors.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            androidx.compose.material3.DropdownMenu(
                expanded = modelMenuOpen,
                onDismissRequest = { modelMenuOpen = false }
            ) {
                modelOptions.forEach { model ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(model.name) },
                        onClick = {
                            onModelSelected(model)
                            modelMenuOpen = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (message.fromUser) Color(0x33FFFFFF)
                                else Color(0x22161B22)
                            )
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.18f),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = message.text,
                            color = IdeColors.TextPrimary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        val borderSpin = rememberInfiniteTransition(label = "neon_border")
        val angle by borderSpin.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "neon_border_angle"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithCache {
                    val brush = Brush.sweepGradient(
                        listOf(
                            Color(0xFFFF3B3B),
                            Color(0xFF3BFF4F),
                            Color(0xFF3B82FF),
                            Color(0xFFFF3B3B)
                        )
                    )
                    onDrawWithContent {
                        drawContent()
                        rotate(angle) {
                            drawRoundRect(
                                brush = brush,
                                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                }
                .clip(RoundedCornerShape(12.dp))
                .background(IdeColors.Bg)
                .padding(2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(IdeColors.Bg)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    textStyle = TextStyle(color = IdeColors.TextPrimary, fontSize = 13.sp),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (input.isEmpty()) {
                            Text(
                                CHAT_PLACEHOLDER_TEXT,
                                color = IdeColors.TextMuted,
                                fontSize = 13.sp
                            )
                        }
                        inner()
                    }
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onSend) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Send",
                        tint = IdeColors.AccentGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalConsoleView(logs: List<String>) {
    val listState = rememberLazyListState()

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

        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(8.dp),
            textStyle = TextStyle(
                color = IdeColors.TextPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp
            )
        )
    }
}

@Composable
private fun PaneTabStrip(
    activePane: IdePane,
    onSelect: (IdePane) -> Unit
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
            IdePane.TERMINAL to "TERMINAL",
            IdePane.EDITOR to "EDITOR",
            IdePane.FILE_TREE to "FILES"
        )
        tabs.forEach { (pane, label) ->
            PaneTab(
                label = label,
                isActive = pane == activePane,
                onClick = { onSelect(pane) }
            )
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun PaneTab(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isActive) IdeColors.AccentBlue.copy(alpha = 0.12f) else Color.Transparent
    val border = if (isActive) IdeColors.AccentBlue.copy(alpha = 0.60f) else IdeColors.Border
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
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp
        )
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
            text = label,
            color = accent.copy(alpha = 0.4f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
