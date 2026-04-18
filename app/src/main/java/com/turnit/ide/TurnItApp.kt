package com.turnit.ide

import android.app.Application
import android.content.Intent
import android.util.Log

class TurnItApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            val stackTrace = Log.getStackTraceString(e)
            val intent = Intent(this, CrashActivity::class.java).apply {
                putExtra("CRASH_LOG", stackTrace)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            try {
                startActivity(intent)
            } catch (launchError: Throwable) {
                Log.e("TurnItApp", "Failed to launch CrashActivity", launchError)
                previousHandler?.uncaughtException(thread, e)
            } finally {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }
}
