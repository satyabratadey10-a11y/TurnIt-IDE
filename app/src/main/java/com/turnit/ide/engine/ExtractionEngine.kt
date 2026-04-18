package com.turnit.ide.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ExtractionEngine(private val appContext: Context? = null) {
    companion object {
        private const val TAG = "ExtractionEngine"
    }

    suspend fun bootstrapEnvironment(context: Context? = appContext): Boolean = withContext(Dispatchers.IO) {
        val targetContext = context ?: return@withContext false
        runCatching {
            val prootFile = File(targetContext.filesDir, "proot")
            val rootfsDir = File(targetContext.filesDir, "rootfs")

            if (prootFile.exists() && rootfsDir.exists()) {
                return@withContext true
            }

            targetContext.assets.open("proot").use { input ->
                prootFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            prootFile.setExecutable(true, false)

            val tempFile = File(targetContext.cacheDir, "ubuntu.tar.gz")
            targetContext.assets.open("ubuntu.tar.gz").use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            rootfsDir.mkdirs()

            val process = ProcessBuilder(
                "tar",
                "-xf",
                tempFile.absolutePath,
                "-C",
                rootfsDir.absolutePath
            ).redirectErrorStream(true).start()
            val processOutput = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            tempFile.delete()
            if (exitCode != 0) {
                Log.e(TAG, "Bootstrap extraction failed (exit=$exitCode): $processOutput")
            }
            exitCode == 0
        }.getOrDefault(false)
    }
}
