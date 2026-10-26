package com.gudangjoker.ararnote.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.gudangjoker.ararnote.R
import com.gudangjoker.ararnote.data.AppDatabase
import com.gudangjoker.ararnote.data.ChecklistItem
import com.gudangjoker.ararnote.data.NoteColor
import com.gudangjoker.ararnote.data.NoteType
import com.gudangjoker.ararnote.ui.ChecklistNoteActivity
import com.gudangjoker.ararnote.ui.MainActivity
import com.gudangjoker.ararnote.ui.TextNoteActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val db = AppDatabase.getDatabase(context)

        CoroutineScope(Dispatchers.IO).launch {
            for (widgetId in appWidgetIds) {
                val noteId = prefs.getLong(PREF_PREFIX_KEY + widgetId, -1L)
                val views = RemoteViews(context.packageName, R.layout.widget_note)

                if (noteId != -1L) {
                    val note = db.noteDao().getNoteById(noteId)
                    if (note != null) {
                        val color = NoteColor.fromIndex(note.colorIndex)
                        val bgColor = ContextCompat.getColor(context, color.bgResId)

                        views.setInt(R.id.widget_root, "setBackgroundColor", bgColor)

                        if (note.encrypted == 1) {
                            views.setTextViewText(R.id.widget_title, "🔒 Locked Note")
                            views.setTextViewText(R.id.widget_content, "Tap to open and enter password")
                        } else {
                            views.setTextViewText(R.id.widget_title, note.title.ifBlank { "Untitled" })
                            if (note.type == NoteType.CHECKLIST) {
                                val items = ChecklistItem.fromJson(note.note)
                                val preview = items.take(5).joinToString("\n") { (if (it.checked) "☑ " else "☐ ") + it.text }
                                views.setTextViewText(R.id.widget_content, preview)
                            } else {
                                views.setTextViewText(R.id.widget_content, note.note)
                            }
                        }

                        val targetActivity = if (note.type == NoteType.CHECKLIST) ChecklistNoteActivity::class.java else TextNoteActivity::class.java
                        val clickIntent = Intent(context, targetActivity).apply {
                            putExtra("extra_note_id", note.id)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            context,
                            widgetId,
                            clickIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                    } else {
                        setDefaultWidgetView(context, views, widgetId)
                    }
                } else {
                    setDefaultWidgetView(context, views, widgetId)
                }

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }

    private fun setDefaultWidgetView(context: Context, views: RemoteViews, widgetId: Int) {
        views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
        views.setTextViewText(R.id.widget_content, "Tap to open ArAr Note")

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        for (widgetId in appWidgetIds) {
            prefs.remove(PREF_PREFIX_KEY + widgetId)
        }
        prefs.apply()
    }

    companion object {
        const val PREFS_NAME = "com.gudangjoker.ararnote.widget.NoteWidgetProvider"
        const val PREF_PREFIX_KEY = "appwidget_"
    }
}
