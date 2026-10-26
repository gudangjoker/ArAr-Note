package com.gudangjoker.ararnote.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gudangjoker.ararnote.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED) {

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val upcomingNotes = db.noteDao().getUpcomingReminders(System.currentTimeMillis())
                    for (note in upcomingNotes) {
                        ReminderScheduler.scheduleReminder(context, note)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
