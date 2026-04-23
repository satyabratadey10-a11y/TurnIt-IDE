package com.turnit.ide.ui

import android.animation.ValueAnimator
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.content.res.AppCompatResources
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.turnit.ide.R
import com.turnit.ide.ai.AiModel
import com.turnit.ide.ai.AiChatClient
import com.turnit.ide.ai.ChatMessage
import com.turnit.ide.engine.ExtractionEngine
import com.turnit.ide.engine.ShellEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class IdePane { TERMINAL, EDITOR, FILE_TREE }

private const val CHAT_PLACEHOLDER_TEXT = "Type your message..."
private val SPLITTER_HANDLE_COLOR = Color(0x88999999)
private const val FILE_TREE_INDENT = "  "
private const val FILE_TREE_DIR_ICON = "📁"
private const val FILE_TREE_FILE_ICON = "📄"
private const val TERMINAL_PROMPT_SUFFIX = " \$ "
private const val TERMINAL_EXECUTION_RESTORE_DELAY_MS = 1_000L
private const val SHELL_SESSION_POLL_INTERVAL_MS = 200L
private const val SHELL_SESSION_INACTIVE_CHECK_LIMIT = 10

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
            "TurnIt IDE Shell Engine (v2.0)\n",
            "Waiting for command...\n"
        )
    }
    var terminalInput by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }
    var currentDir by remember { mutableStateOf("~") }
    var executionResetJob by remember { mutableStateOf<Job?>(null) }
    var executionNonce by remember { mutableStateOf(0) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var hasShellStarted by remember { mutableStateOf(false) }
    var isShellReady by remember { mutableStateOf(false) }
    var isExtractingRootfs by remember { mutableStateOf(false) }

    val testCompileCommand = "echo 'Testing Compilers...'; gcc --version; javac -version; pwd; ls -la"
    val startShellSession = {
        if (isShellReady && !isRunning && !hasShellStarted) {
            hasShellStarted = true
            isRunning = true
            onRunBuild()
            shellEngine.setOutputCallback { output ->
                consoleLogs.add(output)
            }
            val rootfsPath = File(context.filesDir, "rootfs").absolutePath
            shellEngine.startProot(rootfsPath)
            activeJob = scope.launch {
                var sawActiveSession = false
                var consecutiveInactiveChecks = 0
                while (hasShellStarted) {
                    val sessionActive = shellEngine.isSessionActive
                    if (sessionActive) {
                        sawActiveSession = true
                        consecutiveInactiveChecks = 0
                    } else {
                        consecutiveInactiveChecks += 1
                        // Stop when a previously active session ends, or when startup never
                        // transitions to active within the configured grace window.
                        if (sawActiveSession || consecutiveInactiveChecks >= SHELL_SESSION_INACTIVE_CHECK_LIMIT) {
                            isRunning = false
                            hasShellStarted = false
                            onStopBuild()
                            break
                        }
                    }
                    delay(SHELL_SESSION_POLL_INTERVAL_MS)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val rootfsDir = File(context.filesDir, "rootfs")
        val prootBin = File(context.filesDir, "proot")
        val shouldExtract = !rootfsDir.exists() || !prootBin.exists() || !prootBin.canExecute()

        if (shouldExtract) {
            activePane = IdePane.TERMINAL
            isExtractingRootfs = true
            consoleLogs.add("Extracting Ubuntu RootFS... Please wait.\n")
            val extracted = withContext(Dispatchers.IO) {
                ExtractionEngine(context).bootstrapEnvironment(context) { output ->
                    consoleLogs.add(output)
                }
            }
            isExtractingRootfs = false
            consoleLogs.add("[Ubuntu RootFS extraction complete]\n")
            isShellReady = true
            
            // Wire the callback and force the V2 engine to boot the minimal shell
            shellEngine.setOutputCallback { output -> 
                consoleLogs.add(output + "\n") 
            }
            shellEngine.startProot(rootfsDir.absolutePath, "/bin/sh")
        } else {
            isShellReady = true
            
            // If already extracted, wire the callback and boot immediately
            shellEngine.setOutputCallback { output -> 
                consoleLogs.add(output + "\n") 
            }
            shellEngine.startProot(rootfsDir.absolutePath, "/bin/sh")
        }
    }

    LaunchedEffect(isShellReady) {
        startShellSession()
    }
    
    val runCommand: (String) -> Boolean = run@{ command ->
        val trimmed = command.trim()
        if (trimmed.isBlank()) return@run false
        activePane = IdePane.TERMINAL
        if (!isShellReady || isExtractingRootfs) {
            consoleLogs.add("[Shell unavailable while RootFS is preparing]\n")
            return@run false
        }
        if (!isRunning) {
            startShellSession()
            consoleLogs.add("[PRoot shell is starting, please retry command]\n")
            return@run false
        }
        consoleLogs.add("\n$ $trimmed\n")
        if (shellEngine.isSessionActive != true) {
            consoleLogs.add("[Failed to send input to PRoot shell]\n")
            return@run false
        }
        shellEngine.sendInput(trimmed)
        true
    }
    val handleRunClick = { runCommand(testCompileCommand) }
    val handleTerminalSubmit = {
        val command = terminalInput.trim()
        if (command.isNotBlank()) {
            terminalInput = ""
            executionResetJob?.cancel()
            executionNonce += 1
            val submitNonce = executionNonce
            isExecuting = true
            if (command.startsWith("cd ")) {
                val targetDir = command.removePrefix("cd ").trim()
                if (targetDir.isNotBlank()) {
                    currentDir = targetDir
                }
            }
            val submitted = runCommand(command)
            if (!submitted) {
                isExecuting = false
            } else {
                executionResetJob = scope.launch {
                    delay(TERMINAL_EXECUTION_RESTORE_DELAY_MS)
                    if (executionNonce == submitNonce) {
                        isExecuting = false
                    }
                }
            }
        }
    }

    val handleStopClick = {
        if (isRunning) {
            shellEngine.stop()
            activeJob?.cancel()
            consoleLogs.add("\n[Process Killed by User]\n")
            isRunning = false
            hasShellStarted = false
            onStopBuild()
        }
    }

    val addCustomModelOption = remember {
        AiModel(
            name = "+ Add Custom Model",
            modelId = "",
            apiUrl = "",
            apiKey = ""
        )
    }
    val modelOptions = remember {
        val geminiOpenAiEndpoint = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
        mutableStateListOf(
            AiModel(
                "Gemini 3 Flash",
                "gemini-3-flash",
                geminiOpenAiEndpoint,
                ""
            ),
            AiModel(
                "Gemini 2.5 Fast",
                "gemini-2.5-flash",
                geminiOpenAiEndpoint,
                ""
            ),
            AiModel(
                "qwen-plus",
                "qwen-plus",
                "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                ""
            ),
            addCustomModelOption
        )
    }
    var selectedModel by remember { mutableStateOf(modelOptions.first()) }
    var showCustomModelDialog by remember { mutableStateOf(false) }
    var customModelName by remember { mutableStateOf("") }
    var customModelId by remember { mutableStateOf("") }
    var customModelUrl by remember { mutableStateOf("") }
    var customModelApiKey by remember { mutableStateOf("") }
    val clearCustomModelInputs = {
        customModelName = ""
        customModelId = ""
        customModelUrl = ""
        customModelApiKey = ""
    }
    val isCustomModelUrlValid = remember(customModelUrl) {
        val trimmedUrl = customModelUrl.trim()
        val parsedUrl = Uri.parse(trimmedUrl)
        parsedUrl.scheme == "https" && !parsedUrl.host.isNullOrBlank()
    }
    val isCustomModelInputValid =
        customModelName.isNotBlank() &&
            customModelId.isNotBlank() &&
            isCustomModelUrlValid
    val chatMessages = remember {
        mutableStateListOf(
            ChatMessage(role = "assistant", content = "Welcome to TurnIt AI assistant.")
        )
    }
    var chatInput by remember { mutableStateOf("") }
    val sendChatPrompt = send@{
        val prompt = chatInput.trim()
        if (prompt.isBlank()) {
            return@send
        }
        val modelSnapshot = selectedModel
        val chatHistorySnapshot = chatMessages.toList()

        chatMessages.add(ChatMessage(role = "user", content = prompt))
        chatInput = ""

        val loadingBubble = ChatMessage(role = "assistant", content = "...")
        val loadingBubbleId = loadingBubble.id
        chatMessages.add(loadingBubble)

        scope.launch {
            try {
                val response = AiChatClient.sendMessage(
                    model = modelSnapshot,
                    chatHistory = chatHistorySnapshot,
                    newPrompt = prompt
                )
                chatMessages.add(ChatMessage(role = "assistant", content = response))
            } finally {
                val loadingBubbleIndex = chatMessages.indexOfLast { it.id == loadingBubbleId }
                if (loadingBubbleIndex >= 0) {
                    chatMessages.removeAt(loadingBubbleIndex)
                }
            }
        }
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
                        chatMessages.add(ChatMessage(role = "assistant", content = "New chat started."))
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
                            onSend = sendChatPrompt
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
                                        IdePane.TERMINAL -> TerminalConsoleView(
                                            logs = consoleLogs,
                                            input = terminalInput,
                                            isExecuting = isExecuting,
                                            currentDir = currentDir,
                                            onInputChange = { terminalInput = it },
                                            onSubmit = handleTerminalSubmit
                                        )
                                        IdePane.EDITOR -> CodeEditorView()
                                        IdePane.FILE_TREE -> FileTreePane(filesDir = context.filesDir)
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
                                    onSend = sendChatPrompt
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
                        value = customModelId,
                        onValueChange = { customModelId = it },
                        label = { Text("Model ID (API)") },
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
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newModel = AiModel(
                            name = customModelName.trim(),
                            modelId = customModelId.trim(),
                            apiUrl = customModelUrl.trim(),
                            apiKey = customModelApiKey.trim(),
                            isCustom = true
                        )
                        modelOptions.add(modelOptions.size - 1, newModel)
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
                    horizontalArrangement = if (message.role == "user") Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.18f),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = message.content,
                            color = IdeColors.TextPrimary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(IdeColors.Bg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeonChatInputEditText(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    onSend = onSend
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
private fun TerminalConsoleView(
    logs: List<String>,
    input: String,
    isExecuting: Boolean,
    currentDir: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IdeColors.Bg)
            .padding(8.dp)
    ) {
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f)
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
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isExecuting) {
                Text(
                    text = "Executing command...",
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.semantics {
                        contentDescription = "Executing command"
                    }
                )
            } else {
                Text(
                    text = "$currentDir$TERMINAL_PROMPT_SUFFIX",
                    color = Color.Green,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
                Spacer(Modifier.width(8.dp))
                TextField(
                    value = input,
                    onValueChange = onInputChange,
                    label = { Text("Command") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSend = { onSubmit() }
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onSubmit) {
                    Icon(
                        imageVector = Icons.Filled.Terminal,
                        contentDescription = "Run command",
                        tint = IdeColors.AccentGreen
                    )
                }
            }
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
private fun FileTreePane(filesDir: File) {
    val entries by produceState(initialValue = emptyList<FileTreeEntry>(), key1 = filesDir.absolutePath) {
        value = withContext(Dispatchers.IO) {
            buildFileTreeEntries(filesDir)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IdeColors.Bg)
            .padding(8.dp)
    ) {
        Text(
            text = filesDir.absolutePath,
            color = IdeColors.TextMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (entries.isEmpty()) {
                item {
                    Text(
                        text = "(empty)",
                        color = IdeColors.TextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            } else {
                items(entries) { entry ->
                    Text(
                        text = entry.renderLabel,
                        color = IdeColors.TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private data class FileTreeEntry(
    val file: File,
    val depth: Int,
    val renderLabel: String
)

private fun buildFileTreeEntries(root: File): List<FileTreeEntry> {
    val items = mutableListOf<FileTreeEntry>()
    fun visit(node: File, depth: Int) {
        val children = node.listFiles()
            ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            .orEmpty()
        children.forEach { child ->
            items.add(
                FileTreeEntry(
                    file = child,
                    depth = depth,
                    renderLabel = "${FILE_TREE_INDENT.repeat(depth)}${if (child.isDirectory) FILE_TREE_DIR_ICON else FILE_TREE_FILE_ICON} ${child.name}"
                )
            )
            if (child.isDirectory) {
                visit(child, depth + 1)
            }
        }
    }
    visit(root, 0)
    return items
}

@Composable
private fun NeonChatInputEditText(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSend: () -> Unit
) {
    var rotatingBorder by remember { mutableStateOf<android.graphics.drawable.Drawable?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val borderDrawable = AppCompatResources.getDrawable(ctx, R.drawable.bg_neon_input_rotate)
            rotatingBorder = borderDrawable
            val container = FrameLayout(ctx).apply {
                background = borderDrawable
                val paddingPx = (3 * resources.displayMetrics.density).toInt()
                setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val editText = EditText(ctx).apply {
                setTextColor(IdeColors.TextPrimary.toArgb())
                setHintTextColor(IdeColors.TextMuted.toArgb())
                hint = CHAT_PLACEHOLDER_TEXT
                setSingleLine(true)
                imeOptions = EditorInfo.IME_ACTION_SEND
                setBackgroundColor(IdeColors.Bg.toArgb())
                setPadding(
                    (12 * resources.displayMetrics.density).toInt(),
                    (10 * resources.displayMetrics.density).toInt(),
                    (12 * resources.displayMetrics.density).toInt(),
                    (10 * resources.displayMetrics.density).toInt()
                )
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        onValueChange(s?.toString().orEmpty())
                    }
                })
                setOnEditorActionListener { _, actionId, event ->
                    val isEnter = event?.let {
                        it.keyCode == KeyEvent.KEYCODE_ENTER && it.action == KeyEvent.ACTION_DOWN
                    } == true
                    if (actionId == EditorInfo.IME_ACTION_SEND || isEnter) {
                        onSend()
                        true
                    } else {
                        false
                    }
                }
            }
            container.addView(
                editText,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            container
        },
        update = { container ->
            val editText = container.getChildAt(0) as EditText
            if (editText.text.toString() != value) {
                editText.setText(value)
                editText.setSelection(value.length)
            }
        }
    )

    DisposableEffect(rotatingBorder) {
        val rotateDrawable = rotatingBorder as? android.graphics.drawable.RotateDrawable
        if (rotateDrawable == null) {
            onDispose { }
        } else {
            val animator = ValueAnimator.ofInt(0, 10000).apply {
                duration = 2200L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    rotateDrawable.level = it.animatedValue as Int
                }
                start()
            }
            onDispose {
                animator.cancel()
            }
        }
    }
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
            text = label,
            color = accent.copy(alpha = 0.4f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
