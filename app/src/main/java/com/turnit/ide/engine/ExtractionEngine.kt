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
            prootFile.setExecutable(true, false)

            val rootfsDir = File(targetContext.filesDir, "rootfs")
            if (rootfsDir.exists()) {
                return@withContext true
            }

            val assetManager = targetContext.assets
            val rootFsDir = File(targetContext.filesDir, "rootfs")
            if (!rootFsDir.exists()) rootFsDir.mkdirs()

            val inputStream = BufferedInputStream(assetManager.open("ubuntu.tar.gz"))
            val gzipIn = GzipCompressorInputStream(inputStream)
            val tarIn = TarArchiveInputStream(gzipIn)

            var entry = tarIn.nextTarEntry
            while (entry != null) {
                val destFile = File(rootFsDir, entry.name)
                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    val out = FileOutputStream(destFile)
                    tarIn.copyTo(out)
                    out.close()
                    // Force executable permissions for binaries
                    destFile.setExecutable(true)
                }
                entry = tarIn.nextTarEntry
            }
            tarIn.close()

            true
        } catch (e: Exception) {
            val errorLog = Log.getStackTraceString(e)
            appendOutput("\n[REAL ERROR] $errorLog")
            Log.e(TAG, "Bootstrap extraction failed", e)
            false
        }
    }
}
