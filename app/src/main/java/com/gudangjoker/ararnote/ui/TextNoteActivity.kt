package com.gudangjoker.ararnote.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.gudangjoker.ararnote.ArArNoteApp
import com.gudangjoker.ararnote.R
import com.gudangjoker.ararnote.data.ActiveState
import com.gudangjoker.ararnote.data.NoteColor
import com.gudangjoker.ararnote.data.NoteEntity
import com.gudangjoker.ararnote.data.NoteType
import com.gudangjoker.ararnote.databinding.ActivityTextNoteBinding
import com.gudangjoker.ararnote.export.ExportManager
import com.gudangjoker.ararnote.reminder.ReminderScheduler
import com.gudangjoker.ararnote.security.CryptoManager
import com.gudangjoker.ararnote.security.PasswordManager
import com.gudangjoker.ararnote.ui.viewmodel.NoteViewModel
import com.gudangjoker.ararnote.ui.viewmodel.NoteViewModelFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TextNoteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTextNoteBinding
    private lateinit var passwordManager: PasswordManager

    private val viewModel: NoteViewModel by viewModels {
        NoteViewModelFactory((application as ArArNoteApp).database.noteDao())
    }

    private var currentNoteId: Long = 0L
    private var currentNote: NoteEntity? = null
    private var currentColorIndex: Int = NoteColor.YELLOW.index
    private var isEncrypted: Boolean = false
    private var activePassword: String? = null
    private var reminderTimestamp: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTextNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        passwordManager = PasswordManager(this)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        currentNoteId = intent.getLongExtra("extra_note_id", 0L)
        activePassword = intent.getStringExtra("extra_password")

        if (currentNoteId != 0L) {
            loadExistingNote()
        } else {
            applyThemeColor(NoteColor.YELLOW)
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

                if (isEncrypted && activePassword != null) {
                    try {
                        binding.editTitle.setText(note.title)
                        val decryptedBody = CryptoManager.decrypt(note.note, activePassword!!.toCharArray())
                        binding.editBody.setText(decryptedBody)
                    } catch (e: Exception) {
                        Toast.makeText(this@TextNoteActivity, "Failed to decrypt note content", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    binding.editTitle.setText(note.title)
                    binding.editBody.setText(note.note)
                }

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
        binding.editBody.setTextColor(textColor)
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
            // Unlock note
            isEncrypted = false
            activePassword = null
            invalidateOptionsMenu()
            Toast.makeText(this, R.string.action_unlock, Toast.LENGTH_SHORT).show()
        } else {
            // Lock note
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
                    Toast.makeText(this, "Note locked with Master Password", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.msg_wrong_password, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun saveNote(onComplete: () -> Unit = {}) {
        val titleText = binding.editTitle.text.toString().trim()
        val rawBodyText = binding.editBody.text.toString()

        if (titleText.isBlank() && rawBodyText.isBlank()) {
            onComplete()
            return
        }

        val storedBody = if (isEncrypted && activePassword != null) {
            CryptoManager.encrypt(rawBodyText, activePassword!!.toCharArray())
        } else {
            rawBodyText
        }

        val note = (currentNote ?: NoteEntity()).copy(
            id = currentNoteId,
            type = NoteType.TEXT,
            title = titleText,
            note = storedBody,
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
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, binding.editTitle.text.toString())
                    putExtra(Intent.EXTRA_TEXT, binding.editBody.text.toString())
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)))
                true
            }
            R.id.action_export -> {
                val title = binding.editTitle.text.toString()
                val body = binding.editBody.text.toString()
                val created = currentNote?.createdDate ?: System.currentTimeMillis()
                val modified = System.currentTimeMillis()
                ExportManager.showExportBottomSheet(
                    context = this,
                    title = title,
                    body = body,
                    isChecklist = false,
                    checklistItems = emptyList(),
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
