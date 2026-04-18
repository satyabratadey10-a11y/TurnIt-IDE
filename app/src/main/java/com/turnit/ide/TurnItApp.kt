package com.turnit.ide

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlin.system.exitProcess

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
            var crashActivityScheduled = false
            try {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.set(
                    AlarmManager.RTC,
                    System.currentTimeMillis() + CRASH_ACTIVITY_DELAY_MS,
                    pendingIntent
                )
                crashActivityScheduled = true
            } catch (launchError: Throwable) {
                Log.e("TurnItApp", "Failed to schedule CrashActivity", launchError)
            } finally {
                if (!crashActivityScheduled && previousHandler != null) {
                    previousHandler.uncaughtException(thread, e)
                } else {
                    exitProcess(CRASH_EXIT_CODE)
                }
            }
        }
    }

    private companion object {
        const val CRASH_ACTIVITY_DELAY_MS = 100L
        const val CRASH_EXIT_CODE = 10
    }
}
