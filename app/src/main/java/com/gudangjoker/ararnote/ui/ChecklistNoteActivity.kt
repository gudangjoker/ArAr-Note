package com.gudangjoker.ararnote.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.gudangjoker.ararnote.ArArNoteApp
import com.gudangjoker.ararnote.R
import com.gudangjoker.ararnote.data.ActiveState
import com.gudangjoker.ararnote.data.ChecklistItem
import com.gudangjoker.ararnote.data.NoteColor
import com.gudangjoker.ararnote.data.NoteEntity
import com.gudangjoker.ararnote.data.NoteType
import com.gudangjoker.ararnote.databinding.ActivityChecklistNoteBinding
import com.gudangjoker.ararnote.export.ExportManager
import com.gudangjoker.ararnote.reminder.ReminderScheduler
import com.gudangjoker.ararnote.security.CryptoManager
import com.gudangjoker.ararnote.security.PasswordManager
import com.gudangjoker.ararnote.ui.adapter.ChecklistAdapter
import com.gudangjoker.ararnote.ui.util.DisplayUtils
import com.gudangjoker.ararnote.ui.viewmodel.NoteViewModel
import com.gudangjoker.ararnote.ui.viewmodel.NoteViewModelFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ChecklistNoteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChecklistNoteBinding
    private lateinit var passwordManager: PasswordManager
    private lateinit var adapter: ChecklistAdapter

    private val viewModel: NoteViewModel by viewModels {
        NoteViewModelFactory((application as ArArNoteApp).database.noteDao())
    }

    private var currentNoteId: Long = 0L
    private var currentNote: NoteEntity? = null
    private var currentColorIndex: Int = NoteColor.YELLOW.index
    private var isEncrypted: Boolean = false
    private var activePassword: String? = null
    private var reminderTimestamp: Long = 0L

    private val items = mutableListOf<ChecklistItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DisplayUtils.optimizeWindowForHighRefreshRate(this)
        binding = ActivityChecklistNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        passwordManager = PasswordManager(this)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        currentNoteId = intent.getLongExtra("extra_note_id", 0L)
        activePassword = intent.getStringExtra("extra_password")

        val presetReminder = intent.getLongExtra("extra_preset_reminder", 0L)
        if (presetReminder > 0) {
            reminderTimestamp = presetReminder
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveNote { finish() }
            }
        })

        setupChecklist()

        if (currentNoteId != 0L) {
            loadExistingNote()
        } else {
            applyThemeColor(NoteColor.YELLOW)
            // Add initial empty item
            adapter.addItem()
        }
    }

    private fun setupChecklist() {
        adapter = ChecklistAdapter(items) {
            // Callback when an item changes
        }
        binding.recyclerChecklist.layoutManager = LinearLayoutManager(this)
        binding.recyclerChecklist.adapter = adapter

        binding.btnAddItem.setOnClickListener {
            addNewItemFromInput()
        }

        binding.editNewItem.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addNewItemFromInput()
                true
            } else false
        }

        binding.btnMoveChecked.setOnClickListener {
            adapter.moveCheckedToBottom()
        }

        binding.btnDeleteChecked.setOnClickListener {
            adapter.removeCheckedItems()
        }
    }

    private fun addNewItemFromInput() {
        val text = binding.editNewItem.text.toString().trim()
        if (text.isNotBlank()) {
            adapter.addItem(text)
            binding.editNewItem.setText("")
            binding.recyclerChecklist.smoothScrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun loadExistingNote() {
        lifecycleScope.launch {
            val note = viewModel.getNoteById(currentNoteId)
            if (note != null) {
                currentNote = note
                currentColorIndex = note.colorIndex
                isEncrypted = note.encrypted == 1
                reminderTimestamp = note.reminderDate

                applyThemeColor(NoteColor.fromIndex(currentColorIndex))

                binding.editTitle.setText(note.title)

                val rawJson = if (isEncrypted && activePassword != null) {
                    try {
                        CryptoManager.decrypt(note.note, activePassword!!.toCharArray())
                    } catch (e: Exception) {
                        Toast.makeText(this@ChecklistNoteActivity, "Failed to decrypt checklist", Toast.LENGTH_SHORT).show()
                        ""
                    }
                } else {
                    note.note
                }

                val loaded = ChecklistItem.fromJson(rawJson)
                items.clear()
                items.addAll(loaded)
                if (items.isEmpty()) {
                    items.add(ChecklistItem(text = ""))
                }
                adapter.notifyDataSetChanged()

                invalidateOptionsMenu()
            }
        }
    }

    private fun applyThemeColor(color: NoteColor) {
        currentColorIndex = color.index
        val headerColor = ContextCompat.getColor(this, color.headerResId)
        val bgColor = ContextCompat.getColor(this, color.bgResId)
        val textColor = ContextCompat.getColor(this, color.textResId)

        binding.toolbar.setBackgroundColor(headerColor)
        binding.layoutRoot.setBackgroundColor(bgColor)
        binding.editTitle.setTextColor(textColor)
        window.statusBarColor = headerColor
    }

    private fun showColorPickerDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val grid = view.findViewById<GridLayout>(R.id.grid_colors)

        for (color in NoteColor.entries) {
            val btn = Button(this)
            btn.text = getString(color.nameResId)
            btn.textSize = 12f
            val gd = GradientDrawable()
            gd.cornerRadius = 24f
            gd.setColor(ContextCompat.getColor(this, color.bgResId))
            gd.setStroke(3, ContextCompat.getColor(this, color.headerResId))
            btn.background = gd

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(8, 8, 8, 8)
            }
            btn.layoutParams = params

            btn.setOnClickListener {
                applyThemeColor(color)
                dialog.dismiss()
            }
            grid.addView(btn)
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun showReminderDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_reminder, null)
        val btnDate = view.findViewById<Button>(R.id.btn_select_date)
        val btnTime = view.findViewById<Button>(R.id.btn_select_time)
        val textStatus = view.findViewById<TextView>(R.id.text_selected_time)
        val btnRemove = view.findViewById<Button>(R.id.btn_remove_reminder)

        val cal = Calendar.getInstance()
        if (reminderTimestamp > System.currentTimeMillis()) {
            cal.timeInMillis = reminderTimestamp
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            textStatus.text = "Current: " + sdf.format(Date(reminderTimestamp))
        }

        btnDate.setOnClickListener {
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                btnDate.text = sdf.format(cal.time)
                reminderTimestamp = cal.timeInMillis
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        btnTime.setOnClickListener {
            TimePickerDialog(this, { _, hourOfDay, minute ->
                cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                btnTime.text = sdf.format(cal.time)
                reminderTimestamp = cal.timeInMillis
                textStatus.text = "Scheduled: " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(cal.time)
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }

        btnRemove.setOnClickListener {
            reminderTimestamp = 0L
            if (currentNoteId != 0L) {
                ReminderScheduler.cancelReminder(this, currentNoteId)
            }
            dialog.dismiss()
            Toast.makeText(this, "Reminder removed", Toast.LENGTH_SHORT).show()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun toggleNoteLock() {
        if (!passwordManager.isPasswordSet()) {
            showSetPasswordDialog()
            return
        }

        if (isEncrypted) {
            isEncrypted = false
            activePassword = null
            invalidateOptionsMenu()
            Toast.makeText(this, R.string.action_unlock, Toast.LENGTH_SHORT).show()
        } else {
            promptPasswordForLock()
        }
    }

    private fun showSetPasswordDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_password, null)
        val editPassword = view.findViewById<EditText>(R.id.edit_password)
        val editConfirm = view.findViewById<EditText>(R.id.edit_confirm_password)
        editConfirm.visibility = View.VISIBLE

        AlertDialog.Builder(this)
            .setTitle(R.string.pref_master_password)
            .setView(view)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val p1 = editPassword.text.toString()
                val p2 = editConfirm.text.toString()
                if (p1.isNotBlank() && p1 == p2) {
                    passwordManager.setPassword(p1)
                    activePassword = p1
                    isEncrypted = true
                    invalidateOptionsMenu()
                    Toast.makeText(this, R.string.msg_password_set, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun promptPasswordForLock() {
        val view = layoutInflater.inflate(R.layout.dialog_password, null)
        val editPassword = view.findViewById<EditText>(R.id.edit_password)

        AlertDialog.Builder(this)
            .setTitle(R.string.action_lock)
            .setView(view)
            .setPositiveButton(R.string.action_done) { _, _ ->
                val pwd = editPassword.text.toString()
                if (passwordManager.verifyPassword(pwd)) {
                    isEncrypted = true
                    activePassword = pwd
                    invalidateOptionsMenu()
                    Toast.makeText(this, "Checklist locked with Master Password", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.msg_wrong_password, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun saveNote(onComplete: () -> Unit = {}) {
        val titleText = binding.editTitle.text.toString().trim()
        val nonBlankItems = adapter.getItems().filter { it.text.isNotBlank() }

        if (titleText.isBlank() && nonBlankItems.isEmpty()) {
            onComplete()
            return
        }

        val jsonString = ChecklistItem.toJson(nonBlankItems)
        val storedContent = if (isEncrypted && activePassword != null) {
            CryptoManager.encrypt(jsonString, activePassword!!.toCharArray())
        } else {
            jsonString
        }

        val note = (currentNote ?: NoteEntity()).copy(
            id = currentNoteId,
            type = NoteType.CHECKLIST,
            title = titleText,
            note = storedContent,
            colorIndex = currentColorIndex,
            encrypted = if (isEncrypted) 1 else 0,
            reminderDate = reminderTimestamp,
            reminderType = if (reminderTimestamp > 0) 16 else 0,
            activeState = ActiveState.ACTIVE,
            modifiedDate = System.currentTimeMillis()
        )

        viewModel.saveNote(note) { savedId ->
            currentNoteId = savedId
            currentNote = note.copy(id = savedId)
            if (reminderTimestamp > System.currentTimeMillis()) {
                ReminderScheduler.scheduleReminder(this, currentNote!!)
            }
            com.gudangjoker.ararnote.widget.NoteWidgetProvider.notifyWidgets(this)
            onComplete()
        }
    }

    override fun onPause() {
        super.onPause()
        saveNote()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.note_editor_menu, menu)
        val lockItem = menu.findItem(R.id.action_lock)
        lockItem.setIcon(if (isEncrypted) R.drawable.ic_lock else R.drawable.ic_lock_open)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                saveNote { finish() }
                true
            }
            R.id.action_color -> {
                showColorPickerDialog()
                true
            }
            R.id.action_reminder -> {
                showReminderDialog()
                true
            }
            R.id.action_lock -> {
                toggleNoteLock()
                true
            }
            R.id.action_share -> {
                val formattedText = adapter.getItems().joinToString("\n") { (if (it.checked) "[x] " else "[ ] ") + it.text }
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, binding.editTitle.text.toString())
                    putExtra(Intent.EXTRA_TEXT, formattedText)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)))
                true
            }
            R.id.action_export -> {
                val title = binding.editTitle.text.toString()
                val checklistItems = adapter.getItems()
                val created = currentNote?.createdDate ?: System.currentTimeMillis()
                val modified = System.currentTimeMillis()
                ExportManager.showExportBottomSheet(
                    context = this,
                    title = title,
                    body = "",
                    isChecklist = true,
                    checklistItems = checklistItems,
                    createdDate = created,
                    modifiedDate = modified,
                    colorIndex = currentColorIndex
                )
                true
            }
            R.id.action_archive -> {
                currentNote?.let {
                    viewModel.moveToArchive(it)
                    Toast.makeText(this, R.string.msg_note_archived, Toast.LENGTH_SHORT).show()
                    finish()
                }
                true
            }
            R.id.action_delete -> {
                currentNote?.let {
                    viewModel.moveToTrash(it)
                    Toast.makeText(this, R.string.msg_note_deleted, Toast.LENGTH_SHORT).show()
                    finish()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
