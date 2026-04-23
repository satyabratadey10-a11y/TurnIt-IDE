package com.turnit.ide.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

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
            // FIXED: Matches MainShellScreen's expected path exactly
            val rootfsDir = File(targetContext.filesDir, "ubuntu-rootfs")
            
            // Do not extract if already populated
            if (rootfsDir.exists() && rootfsDir.list()?.isNotEmpty() == true) {
                return@withContext true
            }

            val assetManager = targetContext.assets
            if (!rootfsDir.exists()) rootfsDir.mkdirs()

            // Dynamically find any asset starting with "ubuntu"
            val assetsList = assetManager.list("") ?: emptyArray()
            val targetAsset = assetsList.firstOrNull { it.startsWith("ubuntu") }

            if (targetAsset == null) {
                appendOutput("\n[FATAL] No file starting with 'ubuntu' found in assets folder.")
                return@withContext false
            }

            appendOutput("\n[DEBUG] Found rootfs asset: $targetAsset. Extracting...")

            var rawStream = assetManager.open(targetAsset)
            
            // If it is a gzip file, we MUST decompress it before feeding to TarArchive
            if (targetAsset.endsWith(".gz")) {
                rawStream = GZIPInputStream(rawStream)
            }

            BufferedInputStream(rawStream).use { inputStream ->
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
            // Your custom debug block preserved
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
