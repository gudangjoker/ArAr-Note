package com.gudangjoker.ararnote.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.gudangjoker.ararnote.ArArNoteApp
import com.gudangjoker.ararnote.R
import com.gudangjoker.ararnote.data.ChecklistItem
import com.gudangjoker.ararnote.data.NoteColor
import com.gudangjoker.ararnote.data.NoteEntity
import com.gudangjoker.ararnote.data.NoteType
import com.gudangjoker.ararnote.databinding.ActivityMainBinding
import com.gudangjoker.ararnote.export.ExportManager
import com.gudangjoker.ararnote.security.BiometricHelper
import com.gudangjoker.ararnote.security.CryptoManager
import com.gudangjoker.ararnote.security.PasswordManager
import com.gudangjoker.ararnote.ui.adapter.NoteListAdapter
import com.gudangjoker.ararnote.ui.util.DisplayUtils
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
        DisplayUtils.optimizeWindowForHighRefreshRate(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("arar_note_ui_prefs", Context.MODE_PRIVATE)
        isGridMode = prefs.getBoolean("is_grid_mode", true)
        passwordManager = PasswordManager(this)

        setupTopSearchBar()
        setupRecyclerView()
        setupBottomNav()
        setupFab()
        setupColorFilterChips()
        observeNotes()
    }

    private fun setupTopSearchBar() {
        binding.cardSearch.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        binding.btnViewMode.setImageResource(if (isGridMode) R.drawable.ic_list else R.drawable.ic_grid)
        binding.btnViewMode.setOnClickListener {
            isGridMode = !isGridMode
            prefs.edit().putBoolean("is_grid_mode", isGridMode).apply()
            binding.btnViewMode.setImageResource(if (isGridMode) R.drawable.ic_list else R.drawable.ic_grid)
            updateLayoutManager()
            DisplayUtils.performHapticFeedback(this)
        }

        binding.btnSort.setOnClickListener {
            showSortOptionsDialog()
        }

        binding.btnColorFilter.setOnClickListener {
            val isVisible = binding.scrollColorFilter.visibility == View.VISIBLE
            binding.scrollColorFilter.visibility = if (isVisible) View.GONE else View.VISIBLE
            DisplayUtils.performHapticFeedback(this)
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun showSortOptionsDialog() {
        val currentSort = viewModel.sortOrder.value
        val options = arrayOf(
            getString(R.string.sort_modified),
            getString(R.string.sort_created),
            getString(R.string.sort_title),
            getString(R.string.sort_color),
            getString(R.string.sort_reminder)
        )
        val selectedIndex = when (currentSort) {
            SortOrder.MODIFIED -> 0
            SortOrder.CREATED -> 1
            SortOrder.TITLE -> 2
            SortOrder.COLOR -> 3
            SortOrder.REMINDER -> 4
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.action_sort)
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                val chosenOrder = when (which) {
                    0 -> SortOrder.MODIFIED
                    1 -> SortOrder.CREATED
                    2 -> SortOrder.TITLE
                    3 -> SortOrder.COLOR
                    4 -> SortOrder.REMINDER
                    else -> SortOrder.MODIFIED
                }
                viewModel.setSortOrder(chosenOrder)
                DisplayUtils.performHapticFeedback(this)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun setupRecyclerView() {
        adapter = NoteListAdapter(
            isGrid = isGridMode,
            onNoteClick = { note -> onNoteClicked(note) },
            onNoteLongClick = { note -> showNoteOptionsDialog(note); true }
        )

        binding.recyclerNotes.apply {
            setHasFixedSize(true)
            setItemViewCacheSize(30)
            adapter = this@MainActivity.adapter
        }
        updateLayoutManager()
        setupSwipeToActions()
    }

    private fun setupSwipeToActions() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position in 0 until adapter.currentList.size) {
                    val note = adapter.currentList[position]
                    DisplayUtils.performHapticFeedback(this@MainActivity)
                    if (direction == ItemTouchHelper.RIGHT) {
                        viewModel.moveToArchive(note)
                        Snackbar.make(binding.root, R.string.msg_note_archived, Snackbar.LENGTH_LONG)
                            .setAction(R.string.action_undo) {
                                viewModel.unarchiveNote(note)
                            }
                            .show()
                    } else {
                        viewModel.moveToTrash(note)
                        Snackbar.make(binding.root, R.string.msg_note_deleted, Snackbar.LENGTH_LONG)
                            .setAction(R.string.action_undo) {
                                viewModel.restoreFromTrash(note)
                            }
                            .show()
                    }
                }
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.recyclerNotes)
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
        binding.bottomNavigation.selectedItemId = R.id.nav_notes
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_notes -> {
                    binding.recyclerNotes.smoothScrollToPosition(0)
                    true
                }
                R.id.nav_calendar -> {
                    startActivity(Intent(this, CalendarActivity::class.java))
                    false
                }
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
            DisplayUtils.performHapticFeedback(this)
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
            getString(R.string.action_export),
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
                        handleExportNote(note)
                    }
                    2 -> {
                        viewModel.moveToArchive(note)
                        Toast.makeText(this, R.string.msg_note_archived, Toast.LENGTH_SHORT).show()
                    }
                    3 -> {
                        viewModel.moveToTrash(note)
                        Toast.makeText(this, R.string.msg_note_deleted, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun handleExportNote(note: NoteEntity) {
        if (note.encrypted == 1) {
            if (passwordManager.isBiometricEnabled() && BiometricHelper.isBiometricAvailable(this)) {
                val cryptoObject = passwordManager.getBiometricDecryptCryptoObject()
                BiometricHelper.showBiometricPrompt(
                    activity = this,
                    title = "Unlock to Export",
                    subtitle = "Verify identity to export encrypted note",
                    negativeButtonText = getString(R.string.hint_password),
                    cryptoObject = cryptoObject,
                    onSuccess = { result ->
                        val pwd = passwordManager.decryptBiometricPassword(result.cryptoObject?.cipher)
                        if (pwd != null) {
                            exportWithPassword(note, pwd)
                        } else {
                            promptPasswordForExport(note)
                        }
                    },
                    onFallbackToPassword = { promptPasswordForExport(note) },
                    onError = { promptPasswordForExport(note) }
                )
            } else {
                promptPasswordForExport(note)
            }
        } else {
            proceedExport(note, note.note)
        }
    }

    private fun promptPasswordForExport(note: NoteEntity) {
        val view = layoutInflater.inflate(R.layout.dialog_password, null)
        val editPassword = view.findViewById<EditText>(R.id.edit_password)

        AlertDialog.Builder(this)
            .setTitle("Unlock to Export")
            .setView(view)
            .setPositiveButton(R.string.action_done) { _, _ ->
                val enteredPassword = editPassword.text.toString()
                if (passwordManager.verifyPassword(enteredPassword)) {
                    exportWithPassword(note, enteredPassword)
                } else {
                    Toast.makeText(this, R.string.msg_wrong_password, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun exportWithPassword(note: NoteEntity, password: String) {
        try {
            val decrypted = CryptoManager.decrypt(note.note, password.toCharArray())
            proceedExport(note, decrypted)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to decrypt note for export", Toast.LENGTH_SHORT).show()
        }
    }

    private fun proceedExport(note: NoteEntity, content: String) {
        val isChecklist = note.type == NoteType.CHECKLIST
        val checklistItems = if (isChecklist) ChecklistItem.fromJson(content) else emptyList()
        val body = if (isChecklist) "" else content

        ExportManager.showExportBottomSheet(
            context = this,
            title = note.title,
            body = body,
            isChecklist = isChecklist,
            checklistItems = checklistItems,
            createdDate = note.createdDate,
            modifiedDate = note.modifiedDate,
            colorIndex = note.colorIndex
        )
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
            DisplayUtils.performHapticFeedback(this)
        }

        return tv
    }

    private fun observeNotes() {
        lifecycleScope.launch {
            viewModel.activeNotes.collectLatest { notes ->
                adapter.submitList(notes)
                binding.layoutEmptyState.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}
