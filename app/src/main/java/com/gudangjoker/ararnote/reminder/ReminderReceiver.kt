package com.gudangjoker.ararnote.reminder

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.gudangjoker.ararnote.ArArNoteApp
import com.gudangjoker.ararnote.R
import com.gudangjoker.ararnote.ui.MainActivity

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra(ReminderScheduler.EXTRA_NOTE_ID, -1L)
        val noteTitle = intent.getStringExtra(ReminderScheduler.EXTRA_NOTE_TITLE) ?: "Note Reminder"
        val noteContent = intent.getStringExtra(ReminderScheduler.EXTRA_NOTE_CONTENT) ?: ""

        when (intent.action) {
            ACTION_REMINDER -> {
                showNotification(context, noteId, noteTitle, noteContent)
            }
            ACTION_SNOOZE -> {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(noteId.toInt())

                // Snooze 15 minutes
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
                    action = ACTION_REMINDER
                    putExtra(ReminderScheduler.EXTRA_NOTE_ID, noteId)
                    putExtra(ReminderScheduler.EXTRA_NOTE_TITLE, noteTitle)
                    putExtra(ReminderScheduler.EXTRA_NOTE_CONTENT, noteContent)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    noteId.toInt(),
                    snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val triggerAt = System.currentTimeMillis() + 15 * 60 * 1000
                alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            ACTION_DISMISS -> {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(noteId.toInt())
            }
        }
    }

    private fun showNotification(context: Context, noteId: Long, title: String, content: String) {
        val clickIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val clickPendingIntent = PendingIntent.getActivity(
            context,
            noteId.toInt(),
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(ReminderScheduler.EXTRA_NOTE_ID, noteId)
            putExtra(ReminderScheduler.EXTRA_NOTE_TITLE, title)
            putExtra(ReminderScheduler.EXTRA_NOTE_CONTENT, content)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (noteId + 100000).toInt(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_DISMISS
            putExtra(ReminderScheduler.EXTRA_NOTE_ID, noteId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            (noteId + 200000).toInt(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ArArNoteApp.CHANNEL_REMINDER_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(clickPendingIntent)
            .addAction(R.drawable.ic_alarm, context.getString(R.string.action_snooze), snoozePendingIntent)
            .addAction(R.drawable.ic_check, context.getString(R.string.action_done), dismissPendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(noteId.toInt(), notification)
    }

    companion object {
        const val ACTION_REMINDER = "com.gudangjoker.ararnote.ACTION_REMINDER"
        const val ACTION_SNOOZE = "com.gudangjoker.ararnote.ACTION_SNOOZE"
        const val ACTION_DISMISS = "com.gudangjoker.ararnote.ACTION_DISMISS"
    }
}
