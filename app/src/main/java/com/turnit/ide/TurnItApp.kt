package com.turnit.ide

import android.app.Application
import android.content.Intent
import android.util.Log
import kotlin.concurrent.thread

class TurnItApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { crashedThread, e ->
            val stackTrace = Log.getStackTraceString(e)
            val intent = Intent(this, CrashActivity::class.java).apply {
                putExtra("CRASH_LOG", stackTrace)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            try {
                startActivity(intent)
                thread(isDaemon = false) {
                    Thread.sleep(CRASH_KILL_DELAY_MS)
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            } catch (launchError: Throwable) {
                Log.e(
                    "TurnItApp",
                    "Failed to launch CrashActivity for exception: ${e.message}",
                    launchError
                )
                previousHandler?.uncaughtException(crashedThread, e)
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    private companion object {
        const val CRASH_KILL_DELAY_MS = 100L
    }
}
