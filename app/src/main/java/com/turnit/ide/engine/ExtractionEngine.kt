package com.turnit.ide.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ExtractionEngine {

    suspend fun bootstrapEnvironment(context: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val prootFile = File(context.filesDir, "proot")
            val rootfsDir = File(context.filesDir, "rootfs")

            if (prootFile.exists() && rootfsDir.exists()) {
                return@withContext true
            }

            context.assets.open("proot").use { input ->
                prootFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            prootFile.setExecutable(true, false)

            val tempFile = File(context.cacheDir, "ubuntu.tar.gz")
            context.assets.open("ubuntu.tar.gz").use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            rootfsDir.mkdirs()

            val exitCode = ProcessBuilder(
                "tar",
                "-xf",
                tempFile.absolutePath,
                "-C",
                rootfsDir.absolutePath
            ).start().waitFor()

            tempFile.delete()
            exitCode == 0
        }.getOrDefault(false)
    }
}
