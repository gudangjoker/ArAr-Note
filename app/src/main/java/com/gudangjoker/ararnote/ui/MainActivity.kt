package com.gudangjoker.ararnote.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.gudangjoker.ararnote.ArArNoteApp
import com.gudangjoker.ararnote.R
import com.gudangjoker.ararnote.data.NoteColor
import com.gudangjoker.ararnote.data.NoteEntity
import com.gudangjoker.ararnote.data.NoteType
import com.gudangjoker.ararnote.databinding.ActivityMainBinding
import com.gudangjoker.ararnote.security.BiometricHelper
import com.gudangjoker.ararnote.security.PasswordManager
import com.gudangjoker.ararnote.ui.adapter.NoteListAdapter
import com.gudangjoker.ararnote.ui.viewmodel.NoteViewModel
import com.gudangjoker.ararnote.ui.viewmodel.NoteViewModelFactory
import com.gudangjoker.ararnote.ui.viewmodel.SortOrder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var passwordManager: PasswordManager

    private val viewModel: NoteViewModel by viewModels {
        NoteViewModelFactory((application as ArArNoteApp).database.noteDao())
    }

    private lateinit var adapter: NoteListAdapter
    private var isGridMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("arar_note_ui_prefs", Context.MODE_PRIVATE)
        isGridMode = prefs.getBoolean("is_grid_mode", true)
        passwordManager = PasswordManager(this)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = getString(R.string.title_notes)

        setupRecyclerView()
        setupBottomNav()
        setupFab()
        setupColorFilterChips()
        observeNotes()
    }

    private fun setupRecyclerView() {
        adapter = NoteListAdapter(
            isGrid = isGridMode,
            onNoteClick = { note -> onNoteClicked(note) },
            onNoteLongClick = { note -> showNoteOptionsDialog(note); true }
        )

        updateLayoutManager()
        binding.recyclerNotes.adapter = adapter
    }

    private fun updateLayoutManager() {
        if (isGridMode) {
            binding.recyclerNotes.layoutManager = GridLayoutManager(this, 2)
        } else {
            binding.recyclerNotes.layoutManager = LinearLayoutManager(this)
        }
        adapter.setGridMode(isGridMode)
    }

    private fun setupBottomNav() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_notes -> true
                R.id.nav_archive -> {
                    startActivity(Intent(this, ArchiveActivity::class.java))
                    false
                }
                R.id.nav_trash -> {
                    startActivity(Intent(this, TrashActivity::class.java))
                    false
                }
                else -> false
            }
        }
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            showAddNoteTypeDialog()
        }
    }

    private fun showAddNoteTypeDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_add_note_type, null)

        view.findViewById<View>(R.id.btn_type_text).setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, TextNoteActivity::class.java)
            startActivity(intent)
        }

        view.findViewById<View>(R.id.btn_type_checklist).setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, ChecklistNoteActivity::class.java)
            startActivity(intent)
        }

        dialog.setContentView(view)
        dialog.show()
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

    private fun showNoteOptionsDialog(note: NoteEntity) {
        val options = arrayOf(
            if (note.importance == 1) "Unpin Note" else "Pin to Top",
            getString(R.string.action_archive),
            getString(R.string.action_delete)
        )

        AlertDialog.Builder(this)
            .setTitle(note.title.ifBlank { "Note Options" })
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val newImportance = if (note.importance == 1) 0 else 1
                        viewModel.saveNote(note.copy(importance = newImportance, modifiedDate = System.currentTimeMillis()))
                    }
                    1 -> {
                        viewModel.moveToArchive(note)
                        Toast.makeText(this, R.string.msg_note_archived, Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        viewModel.moveToTrash(note)
                        Toast.makeText(this, R.string.msg_note_deleted, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun setupColorFilterChips() {
        val container = binding.layoutColorChips
        container.removeAllViews()

        // "All" chip
        val allView = createColorChipView("All", null)
        container.addView(allView)

        for (color in NoteColor.entries) {
            val chip = createColorChipView(getString(color.nameResId), color.index)
            container.addView(chip)
        }
    }

    private fun createColorChipView(label: String, colorIndex: Int?): View {
        val tv = TextView(this)
        tv.text = label
        tv.textSize = 13f
        tv.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        tv.setPadding(24, 12, 24, 12)

        val gd = GradientDrawable()
        gd.cornerRadius = 32f
        if (colorIndex != null) {
            val c = NoteColor.fromIndex(colorIndex)
            gd.setColor(ContextCompat.getColor(this, c.bgResId))
            gd.setStroke(3, ContextCompat.getColor(this, c.headerResId))
        } else {
            gd.setColor(0xFFE0E0E0.toInt())
        }
        tv.background = gd

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(8, 0, 8, 0)
        }
        tv.layoutParams = params

        tv.setOnClickListener {
            viewModel.setColorFilter(colorIndex)
        }

        return tv
    }

    private fun observeNotes() {
        lifecycleScope.launch {
            viewModel.activeNotes.collectLatest { notes ->
                adapter.submitList(notes)
                binding.textEmpty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val viewModeItem = menu.findItem(R.id.action_view_mode)
        viewModeItem.setIcon(if (isGridMode) R.drawable.ic_list else R.drawable.ic_grid)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                startActivity(Intent(this, SearchActivity::class.java))
                true
            }
            R.id.action_view_mode -> {
                isGridMode = !isGridMode
                prefs.edit().putBoolean("is_grid_mode", isGridMode).apply()
                item.setIcon(if (isGridMode) R.drawable.ic_list else R.drawable.ic_grid)
                updateLayoutManager()
                true
            }
            R.id.sort_by_modified -> {
                viewModel.setSortOrder(SortOrder.MODIFIED)
                true
            }
            R.id.sort_by_created -> {
                viewModel.setSortOrder(SortOrder.CREATED)
                true
            }
            R.id.sort_by_title -> {
                viewModel.setSortOrder(SortOrder.TITLE)
                true
            }
            R.id.sort_by_color -> {
                viewModel.setSortOrder(SortOrder.COLOR)
                true
            }
            R.id.sort_by_reminder -> {
                viewModel.setSortOrder(SortOrder.REMINDER)
                true
            }
            R.id.action_filter_color -> {
                val isVisible = binding.scrollColorFilter.visibility == View.VISIBLE
                binding.scrollColorFilter.visibility = if (isVisible) View.GONE else View.VISIBLE
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
