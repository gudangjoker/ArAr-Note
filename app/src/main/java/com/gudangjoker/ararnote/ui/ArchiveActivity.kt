package com.gudangjoker.ararnote.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
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
import com.gudangjoker.ararnote.security.BiometricHelper
import com.gudangjoker.ararnote.security.PasswordManager
import com.gudangjoker.ararnote.ui.adapter.NoteListAdapter
import com.gudangjoker.ararnote.ui.util.DisplayUtils
import com.gudangjoker.ararnote.ui.viewmodel.NoteViewModel
import com.gudangjoker.ararnote.ui.viewmodel.NoteViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ArchiveActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArchiveBinding
    private lateinit var passwordManager: PasswordManager

    private val viewModel: NoteViewModel by viewModels {
        NoteViewModelFactory((application as ArArNoteApp).database.noteDao())
    }

    private lateinit var adapter: NoteListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DisplayUtils.optimizeWindowForHighRefreshRate(this)
        binding = ActivityArchiveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        passwordManager = PasswordManager(this)

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
        if (note.encrypted == 1) {
            if (passwordManager.isBiometricEnabled() && BiometricHelper.isBiometricAvailable(this)) {
                val cryptoObject = passwordManager.getBiometricDecryptCryptoObject()
                BiometricHelper.showBiometricPrompt(
                    activity = this,
                    title = "Unlock " + (note.title.ifBlank { "Note" }),
                    subtitle = "Verify fingerprint or face to open note",
                    negativeButtonText = getString(R.string.hint_password),
                    cryptoObject = cryptoObject,
                    onSuccess = { result ->
                        val pwd = passwordManager.decryptBiometricPassword(result.cryptoObject?.cipher)
                        if (pwd != null) {
                            openNoteEditor(note, pwd)
                        } else {
                            promptPasswordForNote(note)
                        }
                    },
                    onFallbackToPassword = { promptPasswordForNote(note) },
                    onError = { promptPasswordForNote(note) }
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
