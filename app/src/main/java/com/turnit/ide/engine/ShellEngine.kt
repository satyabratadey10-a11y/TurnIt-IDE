package com.turnit.ide.engine

import android.content.Context
import android.util.Log
import java.io.File
import java.io.InputStream

private const val TAG = "ShellEngine"

class ShellEngine(private val context: Context) {

    private var process: Process? = null
    private var outputCallback: ((String) -> Unit)? = null
    private var isRunning = false

    fun setOutputCallback(callback: (String) -> Unit) {
        outputCallback = callback
    }

    fun startProot(rootfsPath: String, command: String = "/bin/sh") {
        if (isRunning) {
            appendOutput("[ShellEngine-V2] Session already active. Call stop() first.")
            return
        }

        val prootBinary = resolveProotBinary() ?: return

        val prootArgs = buildProotArgs(
            prootBinary = prootBinary,
            rootfsPath  = rootfsPath,
            command     = command
        )

        appendOutput("[ShellEngine-V2] ─────────────────────────────────────")
        appendOutput("[ShellEngine-V2] Launching PRoot session...")
        appendOutput("[ShellEngine-V2] Binary path : ${prootBinary.absolutePath}")
        appendOutput("[ShellEngine-V2] canExecute(): ${prootBinary.canExecute()}")
        appendOutput("[ShellEngine-V2] Rootfs path : $rootfsPath")
        appendOutput("[ShellEngine-V2] Full command: ${prootArgs.joinToString(" ")}")
        appendOutput("[ShellEngine-V2] ─────────────────────────────────────")

        try {
            val pb = ProcessBuilder(prootArgs).apply {
                directory(context.filesDir)
                redirectErrorStream(false)
                environment().apply {
                    put("PROOT_NO_SECCOMP", "1")
                    put("HOME",             "/root")
                    put("TMPDIR",           "/tmp")
                    put("PROOT_TMP_DIR",    context.cacheDir.absolutePath)
                    put("TERM",             "xterm-256color")
                    put("LANG",             "en_US.UTF-8")
                }
            }

            process = pb.start().also { proc ->
                isRunning = true
                pipeStream(proc.inputStream, prefix = "")
                pipeStream(proc.errorStream, prefix = "[ERR] ")
                watchExit(proc)
            }

        } catch (e: Exception) {
            val msg = "[ShellEngine-V2] FATAL: ProcessBuilder threw — ${e.message}"
            Log.e(TAG, msg, e)
            appendOutput(msg)
            isRunning = false
        }
    }

    fun sendInput(text: String) {
        if (process == null || !isRunning) {
            appendOutput("[ShellEngine-V2] No active session.")
            return
        }
        try {
            process!!.outputStream.write((text + "\n").toByteArray())
            process!!.outputStream.flush()
        } catch (e: Exception) {
            appendOutput("[ShellEngine-V2] Input write failed: ${e.message}")
        }
    }

    fun stop() {
        process?.destroy()
        process   = null
        isRunning = false
        appendOutput("[ShellEngine-V2] Session stopped.")
    }

    val isSessionActive: Boolean get() = isRunning

    private fun resolveProotBinary(): File? {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val binary    = File(nativeDir, "libproot.so")

        if (!binary.exists()) {
            appendOutput("[ShellEngine-V2] FATAL: libproot.so missing.")
            return null
        }

        if (!binary.canExecute()) {
            appendOutput("[ShellEngine-V2] FATAL: libproot.so exists but canExecute()=false.")
            return null
        }

        return binary
    }

    private fun buildProotArgs(prootBinary: File, rootfsPath: String, command: String): List<String> = buildList {
        add(prootBinary.absolutePath)
        add("--link2symlink") // <--- THIS IS THE MAGIC FLAG YOU MISSED
        add("-0")
        add("-r"); add(rootfsPath)
        add("-w"); add("/root")
        add("-b"); add("/dev")
        add("-b"); add("/proc")
        add("-b"); add("/sys")
        addAll(command.split(" "))
    }

    private fun pipeStream(stream: InputStream, prefix: String) {
        Thread {
            try {
                stream.bufferedReader().forEachLine { line ->
                    appendOutput("$prefix$line")
                }
            } catch (_: Exception) {}
        }.apply { isDaemon = true; start() }
    }

    private fun watchExit(proc: Process) {
        Thread {
            val code  = proc.waitFor()
            isRunning = false
            appendOutput("[ShellEngine-V2] Process exited — code $code")
        }.apply { isDaemon = true; start() }
    }

    private fun appendOutput(line: String) {
        Log.d(TAG, line)
        outputCallback?.invoke(line)
    }
}
