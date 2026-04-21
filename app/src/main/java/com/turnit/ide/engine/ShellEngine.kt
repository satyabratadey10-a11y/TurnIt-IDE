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
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val prootBinary = File(nativeDir, "libproot.so")
        if (!prootBinary.exists()) {
            throw java.io.FileNotFoundException("libproot.so missing from nativeLibraryDir! Check AGP packagingOptions.")
        }

        // -0: Fake root privileges
        // -r: Target root filesystem
        // -w: Working directory inside rootfs
        // -b: Bind essential Android system directories
        val cmdArgs = listOf(
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

        val pb = ProcessBuilder(cmdArgs)
        pb.redirectErrorStream(true) // Merge stderr into stdout

        val env = pb.environment()
        env.clear()
        env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        env["HOME"] = "/root"
        env["USER"] = "root"
        env["PROOT_NO_SECCOMP"] = "1" // Prevents Android seccomp-bpf crashes
        env["TERM"] = "xterm-256color"

        try {
            val process = pb.start()
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
