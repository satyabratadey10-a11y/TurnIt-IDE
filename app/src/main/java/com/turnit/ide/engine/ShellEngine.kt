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

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    fun setOutputCallback(callback: (String) -> Unit) {
        outputCallback = callback
    }

    /**
     * Starts a PRoot session using the Native Library Bypass.
     * The binary is executed exclusively from nativeLibraryDir — never filesDir.
     *
     * @param rootfsPath Absolute path to the extracted Ubuntu rootfs.
     * @param command    Entry command inside the PRoot environment.
     */
    fun startProot(rootfsPath: String, command: String = "/bin/bash") {
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
                    put("HOME",             context.filesDir.absolutePath)
                    put("TMPDIR",           context.cacheDir.absolutePath)
                    put("PROOT_TMP_DIR",    context.cacheDir.absolutePath)
                    put("LD_LIBRARY_PATH",  context.applicationInfo.nativeLibraryDir)
                    put("PROOT_LOADER",     prootBinary.absolutePath)
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
            val msg = "[ShellEngine-V2] FATAL: ProcessBuilder threw — ${e.message}\n" +
                      "  If path still shows filesDir, another call site was not updated."
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

    // -------------------------------------------------------------------------
    // Native Library Bypass — Binary Resolution
    // filesDir is NEVER used for the binary path.
    // -------------------------------------------------------------------------

    private fun resolveProotBinary(): File? {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val binary    = File(nativeDir, "libproot.so")

        appendOutput("[ShellEngine-V2] nativeLibraryDir   = $nativeDir")
        appendOutput("[ShellEngine-V2] Resolved proot path = ${binary.absolutePath}")

        if (!binary.exists()) {
            appendOutput(
                "[ShellEngine-V2] FATAL: libproot.so missing.\n" +
                "  → Confirm jniLibs/arm64-v8a/libproot.so is in source tree.\n" +
                "  → Confirm extractNativeLibs=\"true\" in AndroidManifest.xml.\n" +
                "  → Confirm no packagingOptions block is compressing the .so."
            )
            return null
        }

        if (!binary.canExecute()) {
            appendOutput(
                "[ShellEngine-V2] FATAL: libproot.so exists but canExecute()=false.\n" +
                "  → OEM SELinux is likely denying exec on this path.\n" +
                "  → Run: adb shell ls -lZ \"${binary.absolutePath}\"\n" +
                "  → Run: adb logcat | grep avc  — look for execmod or execute denial."
            )
            return null
        }

        return binary
    }

    // -------------------------------------------------------------------------
    // PRoot Argument Construction
    // -------------------------------------------------------------------------

    private fun buildProotArgs(prootBinary: File, rootfsPath: String, command: String): List<String> = buildList {
    // ... other args ...
    add("-b"); add("/system/etc/hosts:/etc/hosts")
    add("--") // <--- DELETE THIS EXACT LINE
    addAll(command.split(" "))
}

    // -------------------------------------------------------------------------
    // Stream & Process Monitoring
    // -------------------------------------------------------------------------

    private fun pipeStream(stream: InputStream, prefix: String) {
        Thread {
            try {
                stream.bufferedReader().forEachLine { line ->
                    appendOutput("$prefix$line")
                }
            } catch (_: Exception) {
                // Expected — stream closes when process exits.
            }
        }.apply { isDaemon = true; start() }
    }

    private fun watchExit(proc: Process) {
        Thread {
            val code  = proc.waitFor()
            isRunning = false
            appendOutput("[ShellEngine-V2] Process exited — code $code")
        }.apply { isDaemon = true; start() }
    }

    // -------------------------------------------------------------------------
    // Output Dispatch
    // -------------------------------------------------------------------------

    private fun appendOutput(line: String) {
        Log.d(TAG, line)
        outputCallback?.invoke(line)
    }
}
