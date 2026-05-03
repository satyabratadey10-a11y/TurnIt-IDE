package com.turnit.ide.engine

import android.content.Context
import android.util.Log
import java.io.File

private const val TAG = "ShellEngine"

class ShellEngine(private val context: Context) {

    private var outputCallback: ((String) -> Unit)? = null
    private var isRunning = false

    fun setOutputCallback(callback: (String) -> Unit) {
        outputCallback = callback
    }

    fun startProot(rootfsPath: String, command: String = "") {
        if (isRunning) return
        isRunning = true

        Thread {
            appendOutput("\n[CLAUDE DIAGNOSTIC] Running PRoot pure ptrace test...")
            val prootBinary = resolveProotBinary() ?: return@Thread

            // --version requires NO rootfs, NO loader, and NO mounts. 
            // It only tests if the kernel allows the ptrace syscall.
            val probeCmd = listOf(prootBinary.absolutePath, "--version")

            try {
                val proc = ProcessBuilder(probeCmd)
                    .redirectErrorStream(true)
                    .apply {
                        environment()["PROOT_NO_SECCOMP"] = "1"
                    }
                    .start()

                val output = proc.inputStream.bufferedReader().readText()
                val exitCode = proc.waitFor()

                appendOutput("\n[RESULT] Exit Code: $exitCode")
                if (output.isNotBlank()) appendOutput("[RESULT] Output: \n$output")

                if (exitCode == 0) {
                    appendOutput("\n[SUCCESS] ptrace is ALIVE! The Vivo kernel allows PRoot.")
                    appendOutput("[NEXT STEP] We must extract libproot_loader64.so from Termux.")
                } else if (exitCode == 255 || exitCode == -1) {
                    appendOutput("\n[FATAL] Code $exitCode on --version.")
                    appendOutput("[DIAGNOSIS] Vivo's SELinux is actively blocking ptrace.")
                    appendOutput("[NEXT STEP] PRoot C++ is dead on this device. We must pivot to proot-rs.")
                } else {
                    appendOutput("\n[UNKNOWN] Exited with code $exitCode. Check output.")
                }
            } catch (e: Exception) {
                appendOutput("[ERROR] ${e.message}")
            }
            isRunning = false
        }.start()
    }

    fun sendInput(text: String) {}
    fun stop() { isRunning = false }
    val isSessionActive: Boolean get() = isRunning

    private fun resolveProotBinary(): File? {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val binary = File(nativeDir, "libproot.so")
        if (!binary.exists() || !binary.canExecute()) {
            appendOutput("[FATAL] libproot.so missing or cannot execute.")
            return null
        }
        return binary
    }

    private fun appendOutput(line: String) {
        Log.d(TAG, line)
        outputCallback?.invoke(line)
    }
}
