package com.turnit.ide.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// =======================================================================
// DOWNLOAD PROGRESS STATES
// =======================================================================

sealed class DownloadState {
    object Idle                                          : DownloadState()
    data class Connecting(val url: String)               : DownloadState()
    data class Downloading(
        val bytesReceived: Long,
        val totalBytes:    Long,
        val speedBps:      Long      // bytes/sec
    )                                                    : DownloadState()
    data class Verifying(val progress: Float)            : DownloadState()
    data class Done(val file: File)                      : DownloadState()
    data class Failed(val reason: String, val cause: Throwable? = null)
                                                         : DownloadState()
}

// =======================================================================
// DOWNLOAD ENGINE
//
// Chunked downloader that:
//   1. Streams directly to disk (never holds full payload in RAM)
//   2. Supports resume via HTTP Range header if partial file exists
//   3. Verifies SHA-256 checksum after completion
//   4. Emits typed DownloadState via a cold Flow
//
// Usage:
//   DownloadEngine(context).download(url, destFile, expectedSha256)
//       .collect { state -> handleState(state) }
// =======================================================================

class DownloadEngine(private val context: Context) {

    companion object {
        private const val CHUNK_SIZE     = 256 * 1024   // 256 KB read buffer
        private const val CONNECT_TO_MS  = 15_000
        private const val READ_TO_MS     = 30_000
        private const val SPEED_INTERVAL = 1_000L       // speed sample every 1s
    }

    /**
     * Returns a cold Flow that drives the full download pipeline.
     * Must be collected on an appropriate scope; IO is dispatched internally.
     *
     * @param url          Remote URL of the .7z toolchain payload
     * @param destFile     Target file in context.filesDir (NOT nativeLibraryDir)
     * @param sha256       Expected hex-encoded SHA-256 of the complete file
     */
    fun download(
        url:     String,
        destFile: File,
        sha256:  String
    ): Flow<DownloadState> = flow {
        emit(DownloadState.Connecting(url))

        val conn = runCatching {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TO_MS
                readTimeout    = READ_TO_MS
                setRequestProperty("User-Agent", "TurnIt-IDE/1.0")
                // Resume support
                val existing = if (destFile.exists()) destFile.length() else 0L
                if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
                connect()
            }
        }.getOrElse {
            emit(DownloadState.Failed("Connection failed", it))
            return@flow
        }

        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK &&
            code != HttpURLConnection.HTTP_PARTIAL) {
            conn.disconnect()
            emit(DownloadState.Failed("HTTP $code from server"))
            return@flow
        }

        val isResume       = (code == HttpURLConnection.HTTP_PARTIAL)
        val serverLen      = conn.contentLengthLong
        val existingBytes  = if (isResume && destFile.exists())
                                 destFile.length() else 0L
        val totalBytes     = if (isResume) existingBytes + serverLen
                             else serverLen

        var received       = existingBytes
        var lastSpeedCheck = System.currentTimeMillis()
        var lastBytes      = existingBytes
        var speedBps       = 0L

        runCatching {
            conn.inputStream.use { inp ->
                destFile.parentFile?.mkdirs()
                destFile.outputStream()
                    .let { if (isResume) java.io.FileOutputStream(destFile, true)
                           else it }
                    .buffered(CHUNK_SIZE)
                    .use { out ->
                        val buf = ByteArray(CHUNK_SIZE)
                        while (isActive) {
                            val n = inp.read(buf)
                            if (n == -1) break
                            out.write(buf, 0, n)
                            received += n
                            val now = System.currentTimeMillis()
                            if (now - lastSpeedCheck >= SPEED_INTERVAL) {
                                speedBps = ((received - lastBytes) * 1000L) /
                                            (now - lastSpeedCheck)
                                lastSpeedCheck = now
                                lastBytes = received
                            }
                            emit(
                                DownloadState.Downloading(
                                    bytesReceived = received,
                                    totalBytes    = totalBytes,
                                    speedBps      = speedBps
                                )
                            )
                        }
                    }
            }
        }.onFailure {
            conn.disconnect()
            emit(DownloadState.Failed("I/O error during download", it))
            return@flow
        }

        conn.disconnect()

        // SHA-256 verification
        emit(DownloadState.Verifying(0f))
        val ok = verifySha256(destFile, sha256) { p ->
            // We cannot emit inside a non-suspend lambda here,
            // so the Verifying state is emitted once before and
            // once Done. For fine-grained progress extend this
            // to a channel-backed approach if needed.
        }
        if (ok) {
            emit(DownloadState.Done(destFile))
        } else {
            destFile.delete()
            emit(DownloadState.Failed("SHA-256 mismatch - file deleted"))
        }
    }.flowOn(Dispatchers.IO)

    // ---- SHA-256 verification -----------------------------------------

    private fun verifySha256(
        file:     File,
        expected: String,
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        val digest    = MessageDigest.getInstance("SHA-256")
        val fileSize  = file.length().toFloat()
        var processed = 0L
        val buf       = ByteArray(CHUNK_SIZE)
        file.inputStream().buffered(CHUNK_SIZE).use { inp ->
            while (true) {
                val n = inp.read(buf)
                if (n == -1) break
                digest.update(buf, 0, n)
                processed += n
                if (fileSize > 0) onProgress(processed / fileSize)
            }
        }
        val actual = digest.digest().joinToString("") {
            "%02x".format(it)
        }
        return actual.equals(expected.lowercase(), ignoreCase = true)
    }

    // ---- Utility -------------------------------------------------------

    /** Formats a byte count as a human-readable string (KB, MB, GB). */
    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824 ->
            "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 ->
            "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024 ->
            "%.0f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }
}
