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
            appendOutput("[ShellEngine-V2] Session already active.")
            return
        }

        val nativeDir = context.applicationInfo.nativeLibraryDir
        val proot = File(nativeDir, "libproot.so")
        val loader64 = File(nativeDir, "libproot_loader64.so")

        if (!proot.exists() || !proot.canExecute()) {
            appendOutput("[FATAL] libproot.so missing or non-executable in jniLibs/arm64-v8a.")
            return
        }
        if (!loader64.exists() || !loader64.canExecute()) {
            appendOutput("[FATAL] libproot_loader64.so missing or non-executable.")
            appendOutput("-> You MUST extract it from Termux and place it in jniLibs/arm64-v8a/")
            return
        }

        val safeCommand = if (command == "/bin/sh") "/usr/bin/bash" else command

        val prootArgs = buildList {
            add(proot.absolutePath)
            add("--kill-on-exit")
            add("--link2symlink")
            add("--sysvipc")
            add("-0")
            add("-r"); add(rootfsPath)
            add("-w"); add("/root")
            
            // Core System Mounts
            listOf("/dev", "/proc", "/sys").forEach {
                add("-b"); add(it)
            }
            
            // Host data bridge - allows Ubuntu to read/write Android files
            add("-b"); add("${context.filesDir.absolutePath}:/android/data")
            
            addAll(safeCommand.split(" "))
        }

        appendOutput("[ShellEngine-V2] ─────────────────────────────────────")
        appendOutput("[ShellEngine-V2] Launching Ultimate PRoot Architecture...")
        appendOutput("[ShellEngine-V2] Loader path : ${loader64.absolutePath}")
        appendOutput("[ShellEngine-V2] Full command: ${prootArgs.joinToString(" ")}")
        appendOutput("[ShellEngine-V2] ─────────────────────────────────────")

        try {
            val pb = ProcessBuilder(prootArgs).apply {
                directory(context.filesDir)
                redirectErrorStream(false)
                
                environment().apply {
                    // CRITICAL W^X BYPASS: Point PRoot to the executable native library
                    put("PROOT_LOADER", loader64.absolutePath)
                    
                    val loader32 = File(nativeDir, "libproot_loader.so")
                    if (loader32.exists()) put("PROOT_LOADER_32", loader32.absolutePath)
                    
                    put("PROOT_NO_SECCOMP", "1")
                    put("PROOT_TMP_DIR", context.cacheDir.absolutePath)
                    put("HOME", "/root")
                    put("TERM", "xterm-256color")
                    put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
                    put("LD_LIBRARY_PATH", "") // Isolates guest OS from Android .so files
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
        process = null
        isRunning = false
        appendOutput("[ShellEngine-V2] Session stopped.")
    }

    val isSessionActive: Boolean get() = isRunning

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
            val code = proc.waitFor()
            isRunning = false
            appendOutput("[ShellEngine-V2] Process exited — code $code")
        }.apply { isDaemon = true; start() }
    }

    private fun appendOutput(line: String) {
        Log.d(TAG, line)
        outputCallback?.invoke(line)
    }
}
