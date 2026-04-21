package com.turnit.ide.engine

import android.content.Context
import android.util.Log
import java.io.File
import java.io.InputStream
import java.io.OutputStream

private const val TAG = "ShellEngine"

class ShellEngine(private val context: Context) {

    private var process: Process? = null
    private var outputCallback: ((String) -> Unit)? = null
    @Volatile
    private var isRunning = false

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    fun setOutputCallback(callback: (String) -> Unit) {
        outputCallback = callback
    }

    /**
     * Launches PRoot using the Native Library Bypass.
     * The libproot.so binary is executed directly from nativeLibraryDir,
     * which is the only path Android guarantees exec-permission on without root.
     *
     * @param rootfsPath Absolute path to the extracted Ubuntu rootfs directory.
     * @param command    Command to run inside the PRoot environment.
     */
    fun startProot(rootfsPath: String, command: String = "/bin/bash") {
        if (isRunning) {
            appendOutput("[ShellEngine] Session already active. Stop it first.")
            return
        }

        val prootBinary = resolveProotBinary() ?: return

        val bindMounts = buildBindMounts()
        val prootArgs  = buildProotArgs(
            prootBinary  = prootBinary,
            rootfsPath   = rootfsPath,
            bindMounts   = bindMounts,
            command      = command
        )

        appendOutput("[ShellEngine-V2] Resolved proot binary: ${prootBinary.absolutePath}")
        appendOutput("[ShellEngine-V2] canExecute() = ${prootBinary.canExecute()}")
        appendOutput("[ShellEngine-V2] Command: ${prootArgs.joinToString(" ")}")

        try {
            val pb = ProcessBuilder(prootArgs).apply {
                directory(context.filesDir)
                redirectErrorStream(false)
                environment().apply {
                    put("PROOT_NO_SECCOMP",     "1")
                    put("HOME",                 context.filesDir.absolutePath)
                    put("TMPDIR",               context.cacheDir.absolutePath)
                    put("PROOT_TMP_DIR",        context.cacheDir.absolutePath)
                    put("LD_LIBRARY_PATH",      context.applicationInfo.nativeLibraryDir)
                    // Prevent proot from trying to write loader state to a
                    // non-exec path, which causes secondary Error 13s on
                    // some OEM ROMs.
                    put("PROOT_LOADER",         prootBinary.absolutePath)
                }
            }

            process = pb.start().also { proc ->
                isRunning = true
                streamOutput(proc.inputStream,  prefix = "")
                streamOutput(proc.errorStream,  prefix = "[ERR] ")
                monitorProcessExit(proc)
            }

        } catch (e: Exception) {
            val msg = "[ShellEngine] FATAL: ProcessBuilder failed — ${e.message}"
            Log.e(TAG, msg, e)
            appendOutput(msg)
            isRunning = false
        }
    }

    fun sendInput(text: String) {
        val proc = process
        if (proc == null || !isRunning) {
            appendOutput("[ShellEngine] No active session to send input to.")
            return
        }
        try {
            proc.outputStream.write((text + "\n").toByteArray())
            proc.outputStream.flush()
        } catch (e: Exception) {
            appendOutput("[ShellEngine] Failed to write input: ${e.message}")
        }
    }

    fun stop() {
        process?.destroy()
        process    = null
        isRunning  = false
        appendOutput("[ShellEngine] Session terminated.")
    }

    val isSessionActive: Boolean get() = isRunning

    // -------------------------------------------------------------------------
    // Binary Resolution — Native Library Bypass
    // -------------------------------------------------------------------------

    /**
     * Resolves the proot binary exclusively from nativeLibraryDir.
     * filesDir is never consulted for the binary path.
     * Returns null and emits a diagnostic if the binary is unusable.
     */
    private fun resolveProotBinary(): File? {
        val nativeDir  = context.applicationInfo.nativeLibraryDir
        val prootFile  = File(nativeDir, "libproot.so")

        appendOutput("[ShellEngine-V2] nativeLibraryDir  = $nativeDir")
        appendOutput("[ShellEngine-V2] Absolute proot path = ${prootFile.absolutePath}")

        if (!prootFile.exists()) {
            appendOutput(
                "[ShellEngine] FATAL: libproot.so not found at ${prootFile.absolutePath}\n" +
                "  → Verify jniLibs/arm64-v8a/libproot.so exists in source tree.\n" +
                "  → Verify extractNativeLibs=\"true\" in AndroidManifest.xml.\n" +
                "  → Verify no packagingOptions block is compressing the .so."
            )
            return null
        }

        if (!prootFile.canExecute()) {
            appendOutput(
                "[ShellEngine] FATAL: libproot.so exists but canExecute()=false.\n" +
                "  → This indicates the OEM ROM has revoked exec on nativeLibraryDir.\n" +
                "  → Run: adb shell ls -lZ ${prootFile.absolutePath}\n" +
                "  → Check logcat for: avc: denied { execmod } or { execute }"
            )
            return null
        }

        return prootFile
    }

    // -------------------------------------------------------------------------
    // PRoot Argument Construction
    // -------------------------------------------------------------------------

    private fun buildBindMounts(): List<Pair<String, String>> {
        return listOf(
            "/proc"           to "/proc",
            "/sys"            to "/sys",
            "/dev"            to "/dev",
            "/dev/pts"        to "/dev/pts",
            context.filesDir.absolutePath to "/host-data"
        )
    }

    private fun buildProotArgs(
        prootBinary : File,
        rootfsPath  : String,
        bindMounts  : List<Pair<String, String>>,
        command     : String
    ): List<String> {
        return buildList {
            add(prootBinary.absolutePath)
            add("--kill-on-exit")
            add("-r"); add(rootfsPath)
            add("-w"); add("/root")

            bindMounts.forEach { (host, guest) ->
                add("-b"); add("$host:$guest")
            }

            // Android-specific: bind the timezone data so date/time works
            // inside the rootfs without a full system install.
            add("-b"); add("/system/etc/hosts:/etc/hosts")

            add("--")
            addAll(command.split(" "))
        }
    }

    // -------------------------------------------------------------------------
    // Stream Handling
    // -------------------------------------------------------------------------

    private fun streamOutput(stream: InputStream, prefix: String) {
        Thread {
            try {
                stream.bufferedReader().forEachLine { line ->
                    appendOutput("$prefix$line")
                }
            } catch (_: Exception) {
                // Stream closed on process exit — expected, not an error.
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun monitorProcessExit(proc: Process) {
        Thread {
            val exitCode = proc.waitFor()
            isRunning    = false
            appendOutput("[ShellEngine] Process exited with code $exitCode")
        }.apply {
            isDaemon = true
            start()
        }
    }

    // -------------------------------------------------------------------------
    // Output
    // -------------------------------------------------------------------------

    private fun appendOutput(line: String) {
        Log.d(TAG, line)
        outputCallback?.invoke(line)
    }
}
