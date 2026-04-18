package com.turnit.ide

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

class TurnItApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            val stackTrace = Log.getStackTraceString(e)
            val intent = Intent(this, CrashActivity::class.java).apply {
                putExtra("CRASH_LOG", stackTrace)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            try {
                startActivity(intent)
                Handler(Looper.getMainLooper()).postDelayed(
                    { android.os.Process.killProcess(android.os.Process.myPid()) },
                    CRASH_KILL_DELAY_MS
                )
            } catch (launchError: Throwable) {
                Log.e("TurnItApp", "Failed to launch CrashActivity", launchError)
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    private companion object {
        const val CRASH_KILL_DELAY_MS = 100L
    }
}
