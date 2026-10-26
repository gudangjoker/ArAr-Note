package com.gudangjoker.ararnote.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gudangjoker.ararnote.ArArNoteApp
import com.gudangjoker.ararnote.R
import com.gudangjoker.ararnote.data.NoteEntity
import com.gudangjoker.ararnote.data.NoteType
import com.gudangjoker.ararnote.databinding.ActivitySearchBinding
import com.gudangjoker.ararnote.security.BiometricHelper
import com.gudangjoker.ararnote.security.PasswordManager
import com.gudangjoker.ararnote.ui.adapter.NoteListAdapter
import com.gudangjoker.ararnote.ui.viewmodel.NoteViewModel
import com.gudangjoker.ararnote.ui.viewmodel.NoteViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var passwordManager: PasswordManager

    private val viewModel: NoteViewModel by viewModels {
        NoteViewModelFactory((application as ArArNoteApp).database.noteDao())
    }

    private lateinit var adapter: NoteListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        passwordManager = PasswordManager(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        adapter = NoteListAdapter(
            isGrid = false,
            onNoteClick = { note -> onNoteClicked(note) },
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

    private fun onNoteClicked(note: NoteEntity) {
        if (note.encrypted == 1) {
            if (passwordManager.isBiometricEnabled() && BiometricHelper.isBiometricAvailable(this)) {
                BiometricHelper.showBiometricPrompt(
                    activity = this,
                    title = "Unlock " + (note.title.ifBlank { "Note" }),
                    subtitle = "Verify fingerprint or face to open note",
                    negativeButtonText = getString(R.string.hint_password),
                    onSuccess = {
                        val pwd = passwordManager.getBiometricPassword()
                        if (pwd != null) {
                            openNoteEditor(note, pwd)
                        } else {
                            promptPasswordForNote(note)
                        }
                    },
                    onFallbackToPassword = {
                        promptPasswordForNote(note)
                    },
                    onError = {
                        promptPasswordForNote(note)
                    }
                )
            } else {
                promptPasswordForNote(note)
            }
        } else {
            openNoteEditor(note)
        }
    }

    private fun promptPasswordForNote(note: NoteEntity) {
        val view = layoutInflater.inflate(R.layout.dialog_password, null)
        val editPassword = view.findViewById<EditText>(R.id.edit_password)

        AlertDialog.Builder(this)
            .setTitle(R.string.action_unlock)
            .setView(view)
            .setPositiveButton(R.string.action_done) { _, _ ->
                val enteredPassword = editPassword.text.toString()
                if (passwordManager.verifyPassword(enteredPassword)) {
                    openNoteEditor(note, enteredPassword)
                } else {
                    Toast.makeText(this, R.string.msg_wrong_password, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun openNoteEditor(note: NoteEntity, password: String? = null) {
        val targetClass = if (note.type == NoteType.CHECKLIST) {
            ChecklistNoteActivity::class.java
        } else {
            TextNoteActivity::class.java
        }
        val intent = Intent(this, targetClass).apply {
            putExtra("extra_note_id", note.id)
            if (password != null) {
                putExtra("extra_password", password)
            }
        }
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
