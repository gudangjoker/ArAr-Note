package com.gudangjoker.ararnote.widget

import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.gudangjoker.ararnote.R
import com.gudangjoker.ararnote.data.AppDatabase
import com.gudangjoker.ararnote.data.ChecklistItem
import kotlinx.coroutines.runBlocking

class ChecklistWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ChecklistRemoteViewsFactory(this.applicationContext, intent)
    }
}

class ChecklistRemoteViewsFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val noteId = intent.getLongExtra("extra_note_id", -1L)
    private var items = listOf<ChecklistItem>()

    override fun onCreate() {
        loadData()
    }

    override fun onDataSetChanged() {
        loadData()
    }

    private fun loadData() {
        if (noteId != -1L) {
            val db = AppDatabase.getDatabase(context)
            val note = runBlocking { db.noteDao().getNoteById(noteId) }
            if (note != null && note.encrypted == 0) {
                items = ChecklistItem.fromJson(note.note)
            } else {
                items = emptyList()
            }
        }
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= items.size) return RemoteViews(context.packageName, R.layout.widget_checklist_item)
        val item = items[position]

        val rv = RemoteViews(context.packageName, R.layout.widget_checklist_item)

        if (item.checked) {
            rv.setImageViewResource(R.id.item_checkbox, R.drawable.ic_check_box)
            val spannable = SpannableString(item.text)
            spannable.setSpan(StrikethroughSpan(), 0, item.text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            rv.setTextViewText(R.id.item_text, spannable)
            rv.setTextColor(R.id.item_text, 0x8037474F.toInt())
        } else {
            rv.setImageViewResource(R.id.item_checkbox, R.drawable.ic_check_box_outline)
            rv.setTextViewText(R.id.item_text, item.text)
            rv.setTextColor(R.id.item_text, 0xFF212121.toInt())
        }

        // Fill-in intent for item click (toggling state)
        val fillInIntent = Intent().apply {
            putExtra(NoteWidgetProvider.EXTRA_NOTE_ID, noteId)
            putExtra(NoteWidgetProvider.EXTRA_ITEM_INDEX, position)
        }
        rv.setOnClickFillInIntent(R.id.item_container, fillInIntent)

        return rv
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
