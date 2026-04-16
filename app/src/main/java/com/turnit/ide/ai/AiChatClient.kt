package com.turnit.ide.ai

import android.content.Context
import com.turnit.ide.engine.ShellEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AiChatClient(
    context: Context,
    private val shellEngine: ShellEngine,
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val allowedRoot: File = context.filesDir.canonicalFile

    suspend fun sendMessage(
        chatHistory: MutableList<JSONObject>,
        selectedModel: String,
        apiKey: String?,
        maxIterations: Int = 5
    ): String = withContext(Dispatchers.IO) {
        return@withContext sendMessageRecursive(
            chatHistory = chatHistory,
            selectedModel = selectedModel,
            apiKey = apiKey?.takeIf { it.isNotBlank() },
            maxIterations = maxIterations,
            iteration = 0
        )
    }

    private suspend fun sendMessageRecursive(
        chatHistory: MutableList<JSONObject>,
        selectedModel: String,
        apiKey: String?,
        maxIterations: Int,
        iteration: Int
    ): String {
        if (iteration >= maxIterations) {
            return "Agent stopped after reaching max tool iterations ($maxIterations)."
        }

        val payload = JSONObject()
            .put("model", resolveModel(selectedModel))
            .put("messages", JSONArray(chatHistory))
            .put("tools", buildToolsArray())
            .put("tool_choice", "auto")

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
        if (!apiKey.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }
        val request = requestBuilder.build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return "AI request failed (${response.code}): ${bodyString.ifBlank { "empty response body" }}"
                }

                val root = JSONObject(bodyString)
                val message = root.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?: return "AI request failed: missing message in response."

                val toolCalls = message.optJSONArray("tool_calls")
                if (toolCalls == null || toolCalls.length() == 0) {
                    val finalText = extractContentText(message).ifBlank { "No response content." }
                    chatHistory.add(
                        JSONObject()
                            .put("role", "assistant")
                            .put("content", finalText)
                    )
                    return finalText
                }

                val assistantToolMessage = JSONObject()
                    .put("role", "assistant")
                    .put("tool_calls", toolCalls)
                if (!message.isNull("content")) {
                    assistantToolMessage.put("content", message.opt("content"))
                }
                chatHistory.add(assistantToolMessage)

                for (index in 0 until toolCalls.length()) {
                    val call = toolCalls.optJSONObject(index) ?: continue
                    val toolCallId = call.optString("id")
                    val function = call.optJSONObject("function")
                    val functionName = function?.optString("name").orEmpty()
                    val rawArguments = function?.optString("arguments").orEmpty()
                    val toolOutput = runCatching {
                        executeTool(functionName, rawArguments)
                    }.getOrElse { throwable ->
                        "Tool execution error: ${throwable.message ?: throwable.toString()}"
                    }

                    chatHistory.add(
                        JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", toolCallId)
                            .put("content", toolOutput)
                    )
                }

                return sendMessageRecursive(
                    chatHistory = chatHistory,
                    selectedModel = selectedModel,
                    apiKey = apiKey,
                    maxIterations = maxIterations,
                    iteration = iteration + 1
                )
            }
        } catch (e: Exception) {
            "AI request exception: ${e.message ?: e.toString()}"
        }
    }

    private suspend fun executeTool(functionName: String, rawArguments: String): String {
        val arguments = parseArguments(rawArguments)
        return when (functionName) {
            "read_file" -> {
                val filePath = arguments.optString("filepath")
                if (filePath.isBlank()) {
                    "Tool read_file error: filepath is required."
                } else {
                    val targetFile = resolveAndValidatePath(filePath)
                    targetFile.readText()
                }
            }

            "write_file" -> {
                val filePath = arguments.optString("filepath")
                val content = arguments.optString("content")
                if (filePath.isBlank()) {
                    "Tool write_file error: filepath is required."
                } else {
                    val file = resolveAndValidatePath(filePath)
                    file.parentFile?.mkdirs()
                    file.writeText(content)
                    "Wrote ${content.length} characters to $filePath"
                }
            }

            "run_terminal_command" -> {
                val command = arguments.optString("command")
                if (command.isBlank()) {
                    "Tool run_terminal_command error: command is required."
                } else if (!isSafeCommand(command)) {
                    "Tool run_terminal_command error: command rejected by safety policy."
                } else {
                    shellEngine.execute(command).toList().joinToString(separator = "")
                }
            }

            else -> "Unknown tool requested: $functionName"
        }
    }

    private fun resolveAndValidatePath(path: String): File {
        val target = File(path).canonicalFile
        val allowedPath = allowedRoot.path
        val targetPath = target.path
        if (targetPath != allowedPath && !targetPath.startsWith("$allowedPath${File.separator}")) {
            throw IllegalArgumentException("Path is outside allowed workspace: $path")
        }
        return target
    }

    private fun isSafeCommand(command: String): Boolean {
        val normalized = command.lowercase()
        if (normalized.length > 5000) return false
        return FORBIDDEN_COMMAND_SNIPPETS.none { snippet -> normalized.contains(snippet) }
    }

    private fun parseArguments(rawArguments: String): JSONObject {
        return runCatching {
            if (rawArguments.isBlank()) JSONObject() else JSONObject(rawArguments)
        }.getOrElse {
            JSONObject().put("raw_arguments", rawArguments)
        }
    }

    private fun extractContentText(message: JSONObject): String {
        val content = message.opt("content") ?: return ""
        return when (content) {
            is String -> content
            is JSONArray -> buildString {
                for (i in 0 until content.length()) {
                    val item = content.opt(i)
                    when (item) {
                        is JSONObject -> {
                            val type = item.optString("type")
                            if (type == "text") {
                                append(item.optString("text"))
                            } else if (item.has("text")) {
                                append(item.optString("text"))
                            } else {
                                append(item.toString())
                            }
                        }

                        is String -> append(item)
                        else -> append(item?.toString().orEmpty())
                    }
                }
            }

            else -> content.toString()
        }
    }

    private fun buildToolsArray(): JSONArray {
        return JSONArray()
            .put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject()
                            .put("name", "read_file")
                            .put("description", "Read a file from the Android device.")
                            .put(
                                "parameters",
                                JSONObject()
                                    .put("type", "object")
                                    .put(
                                        "properties",
                                        JSONObject().put(
                                            "filepath",
                                            JSONObject()
                                                .put("type", "string")
                                                .put("description", "Absolute file path to read.")
                                        )
                                    )
                                    .put("required", JSONArray().put("filepath"))
                            )
                    )
            )
            .put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject()
                            .put("name", "write_file")
                            .put("description", "Write content to a file on the Android device.")
                            .put(
                                "parameters",
                                JSONObject()
                                    .put("type", "object")
                                    .put(
                                        "properties",
                                        JSONObject()
                                            .put(
                                                "filepath",
                                                JSONObject()
                                                    .put("type", "string")
                                                    .put("description", "Absolute file path to write.")
                                            )
                                            .put(
                                                "content",
                                                JSONObject()
                                                    .put("type", "string")
                                                    .put("description", "File content to write.")
                                            )
                                    )
                                    .put("required", JSONArray().put("filepath").put("content"))
                            )
                    )
            )
            .put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject()
                            .put("name", "run_terminal_command")
                            .put("description", "Run a terminal command in the PRoot shell.")
                            .put(
                                "parameters",
                                JSONObject()
                                    .put("type", "object")
                                    .put(
                                        "properties",
                                        JSONObject().put(
                                            "command",
                                            JSONObject()
                                                .put("type", "string")
                                                .put("description", "Shell command to execute.")
                                        )
                                    )
                                    .put("required", JSONArray().put("command"))
                            )
                    )
            )
    }

    private fun resolveModel(selectedModel: String): String {
        return when (selectedModel) {
            "Gemini 3 Flash" -> "google/gemini-3-flash"
            "Gemini 2.5 Fast" -> "google/gemini-2.5-fast"
            "Qwen 3.5" -> "qwen/qwen-3.5"
            else -> selectedModel
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val DEFAULT_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
        private val FORBIDDEN_COMMAND_SNIPPETS = listOf(
            "rm -rf /",
            ":(){",
            "shutdown",
            "reboot",
            "poweroff",
            "mkfs",
            "dd if="
        )
    }
}
