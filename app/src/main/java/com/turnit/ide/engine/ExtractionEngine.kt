package com.turnit.ide.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream

class ExtractionEngine(private val appContext: Context? = null) {
    companion object {
        private const val TAG = "ExtractionEngine"
    }

    suspend fun bootstrapEnvironment(
        context: Context? = appContext,
        appendOutput: (String) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val targetContext = context ?: return@withContext false
        try {
            val prootFile = File(targetContext.filesDir, "proot")
            if (!prootFile.exists()) {
                targetContext.assets.open("proot").use { input ->
                    prootFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            if (prootFile.exists()) {
                val executableSet = prootFile.setExecutable(true, false)
                val readableSet = prootFile.setReadable(true, false)
                val writableSet = prootFile.setWritable(true, false)
                if (!executableSet || !readableSet || !writableSet) {
                    appendOutput("\n[REAL ERROR] Failed to apply required permissions to proot.")
                    Log.e(TAG, "Failed to set permissions on ${prootFile.absolutePath}")
                    return@withContext false
                }
            } else {
                appendOutput("\n[REAL ERROR] Missing proot binary after extraction.")
                Log.e(TAG, "proot binary missing at ${prootFile.absolutePath}")
                return@withContext false
            }

            val rootfsDir = File(targetContext.filesDir, "rootfs")
            if (rootfsDir.exists()) {
                return@withContext true
            }

            val assetManager = targetContext.assets
            if (!rootfsDir.exists()) rootfsDir.mkdirs()

            BufferedInputStream(assetManager.open("ubuntu.tar")).use { inputStream ->
                    TarArchiveInputStream(inputStream).use { tarIn ->
                        var entry = tarIn.nextTarEntry
                        while (entry != null) {
                            val destFile = File(rootfsDir, entry.name)
                            if (entry.isDirectory) {
                                destFile.mkdirs()
                            } else {
                                destFile.parentFile?.mkdirs()
                                FileOutputStream(destFile).use { out ->
                                    tarIn.copyTo(out)
                                }
                                // Force executable permissions for binaries
                                destFile.setExecutable(true)
                            }
                            entry = tarIn.nextTarEntry
                        }
                }
            }

            true
        } catch (e: Exception) {
            val errorLog = Log.getStackTraceString(e)
            appendOutput("\n[REAL ERROR] $errorLog")
            try {
                val assetList = targetContext.assets.list("")?.joinToString("\n- ") ?: "None"
                appendOutput("\n\n[DEBUG] Files physically present in assets folder:\n- $assetList")
            } catch (listEx: Exception) {
                appendOutput("\n[DEBUG] Could not list assets: ${listEx.message}")
            }
            Log.e(TAG, "Bootstrap extraction failed", e)
            false
        }
    }
}
