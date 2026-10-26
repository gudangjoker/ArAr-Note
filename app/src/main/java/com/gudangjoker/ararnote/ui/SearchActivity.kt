package com.gudangjoker.ararnote.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gudangjoker.ararnote.ArArNoteApp
import com.gudangjoker.ararnote.data.NoteEntity
import com.gudangjoker.ararnote.data.NoteType
import com.gudangjoker.ararnote.databinding.ActivitySearchBinding
import com.gudangjoker.ararnote.ui.adapter.NoteListAdapter
import com.gudangjoker.ararnote.ui.viewmodel.NoteViewModel
import com.gudangjoker.ararnote.ui.viewmodel.NoteViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding

    private val viewModel: NoteViewModel by viewModels {
        NoteViewModelFactory((application as ArArNoteApp).database.noteDao())
    }

    private lateinit var adapter: NoteListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        adapter = NoteListAdapter(
            isGrid = false,
            onNoteClick = { note -> openNote(note) },
            onNoteLongClick = { false }
        )

        binding.recyclerSearch.layoutManager = LinearLayoutManager(this)
        binding.recyclerSearch.adapter = adapter

        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performSearch(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Request focus on search input
        binding.editSearch.requestFocus()
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            adapter.submitList(emptyList())
            binding.textEmptySearch.visibility = View.GONE
            return
        }

        lifecycleScope.launch {
            viewModel.searchNotes(query).collectLatest { notes ->
                adapter.submitList(notes)
                binding.textEmptySearch.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
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

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
