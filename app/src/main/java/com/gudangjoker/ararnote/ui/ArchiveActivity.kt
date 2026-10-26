package com.gudangjoker.ararnote.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gudangjoker.ararnote.ArArNoteApp
import com.gudangjoker.ararnote.R
import com.gudangjoker.ararnote.data.NoteEntity
import com.gudangjoker.ararnote.data.NoteType
import com.gudangjoker.ararnote.databinding.ActivityArchiveBinding
import com.gudangjoker.ararnote.ui.adapter.NoteListAdapter
import com.gudangjoker.ararnote.ui.util.DisplayUtils
import com.gudangjoker.ararnote.ui.viewmodel.NoteViewModel
import com.gudangjoker.ararnote.ui.viewmodel.NoteViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ArchiveActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArchiveBinding

    private val viewModel: NoteViewModel by viewModels {
        NoteViewModelFactory((application as ArArNoteApp).database.noteDao())
    }

    private lateinit var adapter: NoteListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DisplayUtils.optimizeWindowForHighRefreshRate(this)
        binding = ActivityArchiveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_archive)

        adapter = NoteListAdapter(
            isGrid = false,
            onNoteClick = { note -> openNote(note) },
            onNoteLongClick = { note -> showArchiveOptions(note); true }
        )

        binding.recyclerArchive.layoutManager = LinearLayoutManager(this)
        binding.recyclerArchive.adapter = adapter

        lifecycleScope.launch {
            viewModel.archivedNotes.collectLatest { notes ->
                adapter.submitList(notes)
                binding.textEmptyArchive.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun openNote(note: NoteEntity) {
        val targetClass = if (note.type == NoteType.CHECKLIST) {
            ChecklistNoteActivity::class.java
        } else {
            TextNoteActivity::class.java
        }
        val intent = Intent(this, targetClass).apply {
            putExtra("extra_note_id", note.id)
        }
        startActivity(intent)
    }

    private fun showArchiveOptions(note: NoteEntity) {
        val options = arrayOf(getString(R.string.action_unarchive), getString(R.string.action_delete))
        AlertDialog.Builder(this)
            .setTitle(note.title.ifBlank { "Archived Note" })
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        viewModel.unarchiveNote(note)
                        Toast.makeText(this, R.string.msg_note_unarchived, Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        viewModel.moveToTrash(note)
                        Toast.makeText(this, R.string.msg_note_deleted, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
