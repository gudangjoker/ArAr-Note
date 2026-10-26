package com.gudangjoker.ararnote.ui

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
import com.gudangjoker.ararnote.databinding.ActivityTrashBinding
import com.gudangjoker.ararnote.ui.adapter.NoteListAdapter
import com.gudangjoker.ararnote.ui.viewmodel.NoteViewModel
import com.gudangjoker.ararnote.ui.viewmodel.NoteViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TrashActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrashBinding

    private val viewModel: NoteViewModel by viewModels {
        NoteViewModelFactory((application as ArArNoteApp).database.noteDao())
    }

    private lateinit var adapter: NoteListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_trash)

        adapter = NoteListAdapter(
            isGrid = false,
            onNoteClick = { note -> showTrashOptions(note) },
            onNoteLongClick = { note -> showTrashOptions(note); true }
        )

        binding.recyclerTrash.layoutManager = LinearLayoutManager(this)
        binding.recyclerTrash.adapter = adapter

        binding.btnEmptyTrash.setOnClickListener {
            confirmEmptyTrash()
        }

        lifecycleScope.launch {
            viewModel.trashNotes.collectLatest { notes ->
                adapter.submitList(notes)
                val isEmpty = notes.isEmpty()
                binding.textEmptyTrash.visibility = if (isEmpty) View.VISIBLE else View.GONE
                binding.btnEmptyTrash.visibility = if (isEmpty) View.GONE else View.VISIBLE
            }
        }
    }

    private fun showTrashOptions(note: NoteEntity) {
        val options = arrayOf(getString(R.string.action_restore), getString(R.string.action_delete_permanent))
        AlertDialog.Builder(this)
            .setTitle(note.title.ifBlank { "Trash Note" })
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        viewModel.restoreFromTrash(note)
                        Toast.makeText(this, R.string.msg_note_restored, Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        viewModel.deletePermanently(note)
                        Toast.makeText(this, "Note deleted permanently", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun confirmEmptyTrash() {
        AlertDialog.Builder(this)
            .setTitle(R.string.action_empty_trash)
            .setMessage("Are you sure you want to permanently delete all notes in trash?")
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.clearTrash()
                Toast.makeText(this, R.string.msg_trash_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
