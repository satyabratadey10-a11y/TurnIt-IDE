package com.turnit.ide.engine

import android.content.Context
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

class ExtractionEngine(private val appContext: Context? = null) {
    suspend fun bootstrapEnvironment(
        context: Context? = appContext,
        appendOutput: (String) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val targetContext = context ?: return@withContext false
        try {
            val rootfsDir = File(targetContext.filesDir, "ubuntu-rootfs")
            
            if (rootfsDir.exists() && rootfsDir.list()?.isNotEmpty() == true) {
                return@withContext true
            }

            val assetManager = targetContext.assets
            rootfsDir.mkdirs()

            // Dynamically locate any ubuntu tarball
            val targetAsset = assetManager.list("")?.firstOrNull { it.startsWith("ubuntu") }
            if (targetAsset == null) {
                appendOutput("\n[FATAL] Missing 'ubuntu' tarball in assets.")
                return@withContext false
            }

            appendOutput("\n[DEBUG] Found rootfs: $targetAsset")
            appendOutput("\n[DEBUG] Extracting... (This takes a minute. Please wait!)")

            var rawStream = assetManager.open(targetAsset)
            if (targetAsset.endsWith(".gz")) {
                rawStream = GZIPInputStream(rawStream)
            }

            BufferedInputStream(rawStream).use { inputStream ->
                TarArchiveInputStream(inputStream).use { tarIn ->
                    var entry = tarIn.nextTarEntry
                    var count = 0
                    while (entry != null) {
                        try {
                            val destFile = File(rootfsDir, entry.name)
                            if (entry.isDirectory) {
                                destFile.mkdirs()
                            } else if (entry.isSymbolicLink) {
                                // Native Android symlink handling
                                try { Os.symlink(entry.linkName, destFile.absolutePath) } catch (_: Exception) {}
                            } else if (entry.isFile) {
                                destFile.parentFile?.mkdirs()
                                FileOutputStream(destFile).use { out -> tarIn.copyTo(out) }
                                destFile.setExecutable(true)
                            }
                            count++
                            if (count % 2000 == 0) {
                                appendOutput("\n[DEBUG] Extracted $count files...")
                            }
                        } catch (_: Exception) {
                            // Silently skip broken hardlinks so the installation doesn't die
                        }
                        entry = tarIn.nextTarEntry
                    }
                }
            }
            
            appendOutput("\n[DEBUG] Extraction complete!")
            true
        } catch (e: Exception) {
            // Truncate the error so the UI doesn't crash trying to render it!
            val shortError = e.toString().take(250)
            appendOutput("\n[REAL ERROR] $shortError")
            false
        }
    }
}
