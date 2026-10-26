package com.gudangjoker.ararnote.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_TOGGLE_CHECKLIST_ITEM) {
            val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
            val itemIndex = intent.getIntExtra(EXTRA_ITEM_INDEX, -1)

            if (noteId != -1L && itemIndex != -1) {
                val db = AppDatabase.getDatabase(context)
                val pendingResult = goAsync()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val note = db.noteDao().getNoteById(noteId)
                        if (note != null && note.type == NoteType.CHECKLIST && note.encrypted == 0) {
                            val items = ChecklistItem.fromJson(note.note)
                            if (itemIndex >= 0 && itemIndex < items.size) {
                                items[itemIndex].checked = !items[itemIndex].checked
                                val updatedNote = note.copy(
                                    note = ChecklistItem.toJson(items),
                                    modifiedDate = System.currentTimeMillis()
                                )
                                db.noteDao().updateNote(updatedNote)

                                // Refresh widgets
                                val appWidgetManager = AppWidgetManager.getInstance(context)
                                val ids = appWidgetManager.getAppWidgetIds(
                                    ComponentName(context, NoteWidgetProvider::class.java)
                                )
                                appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)

                                // Trigger onUpdate to update header progress counter
                                val refreshIntent = Intent(context, NoteWidgetProvider::class.java).apply {
                                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                                }
                                context.sendBroadcast(refreshIntent)
                            }
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

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

                        // Open note editor pending intent
                        val targetActivity = if (note.type == NoteType.CHECKLIST) {
                            ChecklistNoteActivity::class.java
                        } else {
                            TextNoteActivity::class.java
                        }
                        val openIntent = Intent(context, targetActivity).apply {
                            putExtra("extra_note_id", note.id)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        val openPendingIntent = PendingIntent.getActivity(
                            context,
                            widgetId,
                            openIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_header, openPendingIntent)
                        views.setOnClickPendingIntent(R.id.widget_btn_open, openPendingIntent)

                        if (note.encrypted == 1) {
                            views.setTextViewText(R.id.widget_title, "🔒 Locked Note")
                            views.setViewVisibility(R.id.widget_progress, View.GONE)
                            views.setViewVisibility(R.id.widget_content, View.VISIBLE)
                            views.setViewVisibility(R.id.widget_list, View.GONE)
                            views.setTextViewText(R.id.widget_content, "Tap to open and enter password")
                            views.setOnClickPendingIntent(R.id.widget_content, openPendingIntent)
                        } else if (note.type == NoteType.CHECKLIST) {
                            views.setTextViewText(R.id.widget_title, note.title.ifBlank { "Checklist" })

                            val items = ChecklistItem.fromJson(note.note)
                            val doneCount = items.count { it.checked }
                            views.setTextViewText(R.id.widget_progress, "✓ $doneCount/${items.size}")
                            views.setViewVisibility(R.id.widget_progress, View.VISIBLE)

                            views.setViewVisibility(R.id.widget_content, View.GONE)
                            views.setViewVisibility(R.id.widget_list, View.VISIBLE)

                            // Setup RemoteViewsService adapter
                            val serviceIntent = Intent(context, ChecklistWidgetService::class.java).apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                                putExtra("extra_note_id", note.id)
                                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                            }
                            views.setRemoteAdapter(R.id.widget_list, serviceIntent)

                            // Pending intent template for item toggle clicks
                            val toggleIntent = Intent(context, NoteWidgetProvider::class.java).apply {
                                action = ACTION_TOGGLE_CHECKLIST_ITEM
                            }
                            val togglePendingIntent = PendingIntent.getBroadcast(
                                context,
                                widgetId,
                                toggleIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                            )
                            views.setPendingIntentTemplate(R.id.widget_list, togglePendingIntent)
                            appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list)

                        } else {
                            // Text Note
                            views.setTextViewText(R.id.widget_title, note.title.ifBlank { "Untitled" })
                            views.setViewVisibility(R.id.widget_progress, View.GONE)
                            views.setViewVisibility(R.id.widget_list, View.GONE)
                            views.setViewVisibility(R.id.widget_content, View.VISIBLE)
                            views.setTextViewText(R.id.widget_content, note.note.ifBlank { "(Empty note)" })
                            views.setOnClickPendingIntent(R.id.widget_content, openPendingIntent)
                        }
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
        views.setViewVisibility(R.id.widget_progress, View.GONE)
        views.setViewVisibility(R.id.widget_list, View.GONE)
        views.setViewVisibility(R.id.widget_content, View.VISIBLE)
        views.setTextViewText(R.id.widget_content, "Tap to open ArAr Note")

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
        views.setOnClickPendingIntent(R.id.widget_header, pendingIntent)
        views.setOnClickPendingIntent(R.id.widget_content, pendingIntent)
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
        const val ACTION_TOGGLE_CHECKLIST_ITEM = "com.gudangjoker.ararnote.ACTION_TOGGLE_CHECKLIST_ITEM"
        const val EXTRA_NOTE_ID = "extra_note_id"
        const val EXTRA_ITEM_INDEX = "extra_item_index"

        fun notifyWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, NoteWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
                val refreshIntent = Intent(context, NoteWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(refreshIntent)
            }
        }
    }
}
