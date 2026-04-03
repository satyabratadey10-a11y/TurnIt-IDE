package com.turnit.ide.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

sealed class ExtractionState {
    object Idle                                          : ExtractionState()
    object Preparing                                     : ExtractionState()
    data class Extracting(
        val currentFile: String,
        val filesExtracted: Int,
        val percentEstimate: Float
    )                                                    : ExtractionState()
    data class Done(val rootfsDir: File)                 : ExtractionState()
    data class Failed(val reason: String, val cause: Throwable? = null)
                                                         : ExtractionState()
}

class ExtractionEngine(private val context: Context) {

    companion object {
        private const val SEVENZIP_LIB_NAME = "lib7zr.so"
        private val PROGRESS_RE = Regex("""^\s*(\d+)%\s*-\s*(.+)$""")
    }

    private val nativeLibDir: File
        get() = File(context.applicationInfo.nativeLibraryDir)

    private val sevenZipBin: File
        get() = File(nativeLibDir, SEVENZIP_LIB_NAME)

    val rootfsDir: File
        get() = File(context.filesDir, "rootfs")

    fun extract(
        archive: File,
        destDir: File = rootfsDir
    ): Flow<ExtractionState> = flow {
        emit(ExtractionState.Preparing)

        if (!sevenZipBin.exists()) {
            emit(ExtractionState.Failed("7zr binary not found. Ensure lib7zr.so is installed."))
            return@flow
        }

        if (!sevenZipBin.canExecute()) {
            runCatching { sevenZipBin.setExecutable(true, false) }.onFailure {
                emit(ExtractionState.Failed("Cannot chmod 7zr: ${it.message}", it))
                return@flow
            }
        }

        if (!archive.exists()) {
            emit(ExtractionState.Failed("Archive not found: ${archive.absolutePath}"))
            return@flow
        }

        destDir.mkdirs()

        val cmd = listOf(
            sevenZipBin.absolutePath,
            "x",
            archive.absolutePath,
            "-y",
            "-o${destDir.absolutePath}",
            "-mmt=2",
            "-bsp1"
        )

        val proc = runCatching {
            ProcessBuilder(cmd).redirectErrorStream(true).start()
        }.getOrElse {
            emit(ExtractionState.Failed("Failed to start 7zr process", it))
            return@flow
        }

        var filesExtracted = 0
        var lastPercent    = 0f

        runCatching {
            proc.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    val match = PROGRESS_RE.find(line.trim())
                    if (match != null) {
                        val pct  = match.groupValues[1].toFloatOrNull() ?: lastPercent
                        val name = match.groupValues[2]
                        lastPercent = pct
                        filesExtracted++
                        // emit() is safely called inside the while loop
                        emit(ExtractionState.Extracting(name, filesExtracted, pct / 100f))
                    }
                }
            }
        }.onFailure {
            proc.destroyForcibly()
            emit(ExtractionState.Failed("I/O reading 7zr output", it))
            return@flow
        }

        val exitCode = proc.waitFor()
        if (exitCode != 0) {
            emit(ExtractionState.Failed("7zr exited with code $exitCode"))
            return@flow
        }

        emit(ExtractionState.Done(destDir))
    }.flowOn(Dispatchers.IO)

    fun isRootfsPresent(): Boolean =
        rootfsDir.exists() && (rootfsDir.listFiles()?.isNotEmpty() == true)

    fun purgeRootfs() = rootfsDir.deleteRecursively()
}
