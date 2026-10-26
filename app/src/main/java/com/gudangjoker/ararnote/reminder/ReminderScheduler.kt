package com.gudangjoker.ararnote.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.gudangjoker.ararnote.data.NoteEntity

object ReminderScheduler {

    const val EXTRA_NOTE_ID = "extra_note_id"
    const val EXTRA_NOTE_TITLE = "extra_note_title"
    const val EXTRA_NOTE_CONTENT = "extra_note_content"

    fun getRequestCode(noteId: Long, offset: Int = 0): Int {
        val base = (noteId xor (noteId ushr 32)).toInt() and 0x3FFFFFFF
        return base + offset
    }

    fun scheduleReminder(context: Context, note: NoteEntity) {
        if (note.reminderDate <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMINDER
            putExtra(EXTRA_NOTE_ID, note.id)
            putExtra(EXTRA_NOTE_TITLE, if (note.encrypted == 1) "Encrypted Note" else note.title)
            putExtra(EXTRA_NOTE_CONTENT, if (note.encrypted == 1) "Locked content" else note.note)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            getRequestCode(note.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    note.reminderDate,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    note.reminderDate,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // In Android 12+, exact alarm permission might not be granted
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                note.reminderDate,
                pendingIntent
            )
        }
    }

    fun cancelReminder(context: Context, noteId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            getRequestCode(noteId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
