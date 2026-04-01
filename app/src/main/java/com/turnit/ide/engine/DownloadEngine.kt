package com.turnit.ide.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed class DownloadState {
    object Idle                                          : DownloadState()
    data class Connecting(val url: String)               : DownloadState()
    data class Downloading(
        val bytesReceived: Long,
        val totalBytes:    Long,
        val speedBps:      Long
    )                                                    : DownloadState()
    data class Verifying(val progress: Float)            : DownloadState()
    data class Done(val file: File)                      : DownloadState()
    data class Failed(
        val reason: String,
        val cause:  Throwable? = null
    )                                                    : DownloadState()
}

class DownloadEngine(private val context: Context) {

    companion object {
        private const val CHUNK_SIZE     = 256 * 1024
        private const val CONNECT_TO_MS  = 15_000
        private const val READ_TO_MS     = 30_000
        private const val SPEED_INTERVAL = 1_000L
    }

    fun download(
        url:      String,
        destFile: File,
        sha256:   String
    ): Flow<DownloadState> = flow {
        emit(DownloadState.Connecting(url))

        val conn = runCatching {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TO_MS
                readTimeout    = READ_TO_MS
                setRequestProperty("User-Agent", "TurnIt-IDE/1.0")
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

        val isResume      = code == HttpURLConnection.HTTP_PARTIAL
        val serverLen     = conn.contentLengthLong
        val existingBytes = if (isResume && destFile.exists())
            destFile.length() else 0L
        val totalBytes    = if (isResume) existingBytes + serverLen
                            else serverLen

        var received       = existingBytes
        var lastSpeedCheck = System.currentTimeMillis()
        var lastBytes      = existingBytes
        var speedBps       = 0L

        // currentCoroutineContext().isActive is the correct way to check
        // cancellation inside a flow block on Kotlin 1.9+.
        // Plain `isActive` is also valid inside flow{} because flow
        // builders are CoroutineScope extensions.
        runCatching {
            conn.inputStream.use { inp ->
                destFile.parentFile?.mkdirs()
                val out = if (isResume)
                    FileOutputStream(destFile, true).buffered(CHUNK_SIZE)
                else
                    destFile.outputStream().buffered(CHUNK_SIZE)
                out.use { writer ->
                    val buf = ByteArray(CHUNK_SIZE)
                    while (currentCoroutineContext().isActive) {
                        val n = inp.read(buf)
                        if (n == -1) break
                        writer.write(buf, 0, n)
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

        emit(DownloadState.Verifying(0f))
        if (verifySha256(destFile, sha256)) {
            emit(DownloadState.Done(destFile))
        } else {
            destFile.delete()
            emit(DownloadState.Failed("SHA-256 mismatch - file deleted"))
        }
    }.flowOn(Dispatchers.IO)

    private fun verifySha256(file: File, expected: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf    = ByteArray(CHUNK_SIZE)
        file.inputStream().buffered(CHUNK_SIZE).use { inp ->
            while (true) {
                val n = inp.read(buf)
                if (n == -1) break
                digest.update(buf, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expected.lowercase(), ignoreCase = true)
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824L ->
            "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L ->
            "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024L ->
            "%.0f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }
}
