package com.turnit.ide.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedWriter
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class ShellEngine(private val context: Context) {

    private val rootfsDir = File(context.filesDir, "rootfs")
    private val processLock = Any()
    @Volatile private var runningProcess: Process? = null
    @Volatile private var runningWriter: BufferedWriter? = null

    /**
     * Starts an interactive bash shell inside PRoot and streams output until the shell exits.
     */
    fun startInteractiveShell(): Flow<String> = flow {
        if (!rootfsDir.exists()) {
            emit("FATAL: Rootfs not found at ${rootfsDir.absolutePath}\n")
            return@flow
        }

        suspend fun appendOutput(message: String) {
            emit(message)
        }

        val nativeDir = context.applicationInfo.nativeLibraryDir
        val prootBinary = File(nativeDir, "libproot.so")

        // 1. Force the UI to prove what path it is using
        appendOutput("\n[DEBUG] Target execution path: ${prootBinary.absolutePath}")

        // 2. Hard check for existence
        if (!prootBinary.exists()) {
            appendOutput("\n[FATAL] libproot.so is MISSING from nativeLibraryDir! AGP legacy packaging failed.")
            return@flow // Stop execution
        }

        // 3. Hard check for OS execution rights
        if (!prootBinary.canExecute()) {
            appendOutput("\n[FATAL] libproot.so exists but is NOT executable. OS blocked it.")
            return@flow // Stop execution
        }

        // -0: Fake root privileges
        // -r: Target root filesystem
        // -w: Working directory inside rootfs
        // -b: Bind essential Android system directories
        // 4. Execute the correct binary
        val processBuilder = ProcessBuilder(
            prootBinary.absolutePath,
            "--link2symlink",
            "-0",
            "-r", rootfsDir.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-w", "/root",
            "/bin/bash",
            "--login"
        )

        processBuilder.redirectErrorStream(true) // Merge stderr into stdout

        val env = processBuilder.environment()
        env.clear()
        env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        env["HOME"] = "/root"
        env["USER"] = "root"
        env["PROOT_NO_SECCOMP"] = "1" // Prevents Android seccomp-bpf crashes
        env["TERM"] = "xterm-256color"

        try {
            val process = processBuilder.start()
            val writer = BufferedWriter(OutputStreamWriter(process.outputStream))
            synchronized(processLock) {
                runningProcess = process
                runningWriter = writer
            }
            emit("[PRoot shell started]\n")

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                emit(line + "\n")
            }

            val exitCode = process.waitFor()
            emit("\n[Process terminated with code $exitCode]\n")
        } catch (e: Exception) {
            emit("\n[ShellEngine Exception: ${e.stackTraceToString()}]\n")
        } finally {
            synchronized(processLock) {
                runningWriter?.runCatching { close() }
                runningWriter = null
                runningProcess?.runCatching { destroy() }
                runningProcess = null
            }
        }
    }.flowOn(Dispatchers.IO)

    fun sendInput(command: String): Boolean = synchronized(processLock) {
        val writer = runningWriter ?: return false
        return try {
            writer.write(command)
            writer.newLine()
            writer.flush()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stopInteractiveShell() {
        synchronized(processLock) {
            runningWriter?.runCatching { close() }
            runningWriter = null
            runningProcess?.runCatching { destroy() }
            runningProcess = null
        }
    }
}
