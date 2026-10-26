package com.gudangjoker.ararnote.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gudangjoker.ararnote.R
import com.gudangjoker.ararnote.data.AppDatabase
import com.gudangjoker.ararnote.databinding.ActivityWidgetConfigureBinding
import com.gudangjoker.ararnote.ui.adapter.NoteListAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NoteWidgetConfigureActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var binding: ActivityWidgetConfigureBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        binding = ActivityWidgetConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.title = getString(R.string.widget_select_note)
        setSupportActionBar(binding.toolbar)

        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val adapter = NoteListAdapter(
            isGrid = false,
            onNoteClick = { note ->
                saveWidgetPref(note.id)
                val appWidgetManager = AppWidgetManager.getInstance(this)
                val provider = NoteWidgetProvider()
                provider.onUpdate(this, appWidgetManager, intArrayOf(appWidgetId))

                val resultValue = Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                setResult(RESULT_OK, resultValue)
                finish()
            },
            onNoteLongClick = { false }
        )

        binding.recyclerNotes.layoutManager = LinearLayoutManager(this)
        binding.recyclerNotes.adapter = adapter

        val db = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            db.noteDao().getActiveNotesByModifiedDate().collectLatest { notes ->
                adapter.submitList(notes)
            }
        }
    }

    private fun saveWidgetPref(noteId: Long) {
        val prefs = getSharedPreferences(NoteWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE).edit()
        prefs.putLong(NoteWidgetProvider.PREF_PREFIX_KEY + appWidgetId, noteId)
        prefs.apply()
    }
}
