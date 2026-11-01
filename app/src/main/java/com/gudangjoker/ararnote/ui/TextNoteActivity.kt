package com.gudangjoker.ararnote.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.ArrowKeyMovementMethod
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
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
import com.gudangjoker.ararnote.security.BiometricHelper
import com.gudangjoker.ararnote.security.CryptoManager
import com.gudangjoker.ararnote.security.PasswordManager
import com.gudangjoker.ararnote.ui.util.DisplayUtils
import com.gudangjoker.ararnote.ui.viewmodel.NoteViewModel
import com.gudangjoker.ararnote.ui.viewmodel.NoteViewModelFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.ArrayDeque
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

    // Read Mode vs Edit Mode
    private var isEditMode: Boolean = true

    // Undo / Redo Stacks
    private data class TextSnapshot(val title: String, val body: String)
    private val undoStack = ArrayDeque<TextSnapshot>()
    private val redoStack = ArrayDeque<TextSnapshot>()
    private var isUndoOrRedoAction = false
    private var textWatcherActive = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DisplayUtils.optimizeWindowForHighRefreshRate(this)
        binding = ActivityTextNoteBinding.inflate(layoutInflater)
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

        setupTextWatchersForUndo()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackAction()
            }
        })

        // Check for Intent.ACTION_SEND (Sharing text to ArAr Note)
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            handleIncomingShareIntent(intent)
        } else if (currentNoteId != 0L) {
            // Existing note: start in Read Mode
            isEditMode = false
            loadExistingNote()
        } else {
            // New note: start in Edit Mode
            isEditMode = true
            applyThemeColor(NoteColor.YELLOW)
            setEditMode(true)
        }
    }

    private fun handleIncomingShareIntent(intent: Intent) {
        val extraSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""

        currentNoteId = 0L
        applyThemeColor(NoteColor.YELLOW)

        if (!extraSubject.isNullOrBlank()) {
            binding.editTitle.setText(extraSubject)
            binding.editBody.setText(extraText)
        } else {
            val lines = extraText.trim().split("\n")
            if (lines.isNotEmpty() && lines[0].length <= 50) {
                binding.editTitle.setText(lines[0])
                binding.editBody.setText(lines.drop(1).joinToString("\n").trim())
            } else {
                binding.editBody.setText(extraText)
            }
        }

        isEditMode = true
        setEditMode(true)
        Toast.makeText(this, "Shared to ArAr Note", Toast.LENGTH_SHORT).show()
    }

    private fun handleBackAction() {
        if (isEditMode && currentNoteId != 0L) {
            // Save and toggle back to Read Mode
            saveNote {
                setEditMode(false)
            }
        } else {
            // Save and finish activity
            saveNote { finish() }
        }
    }

    private fun setEditMode(enable: Boolean) {
        isEditMode = enable
        if (enable) {
            // EDIT MODE
            binding.editTitle.isFocusable = true
            binding.editTitle.isFocusableInTouchMode = true
            binding.editTitle.isCursorVisible = true

            binding.editBody.isFocusable = true
            binding.editBody.isFocusableInTouchMode = true
            binding.editBody.isCursorVisible = true
            binding.editBody.movementMethod = ArrowKeyMovementMethod.getInstance()
            binding.editBody.setOnClickListener(null)

            if (binding.editTitle.text.isNullOrBlank()) {
                binding.editTitle.requestFocus()
            } else {
                binding.editBody.requestFocus()
                binding.editBody.setSelection(binding.editBody.text.length)
            }

            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(if (binding.editTitle.isFocused) binding.editTitle else binding.editBody, InputMethodManager.SHOW_IMPLICIT)
        } else {
            // READ / VIEW MODE with active Linkify
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)

            binding.editTitle.isFocusable = false
            binding.editTitle.isFocusableInTouchMode = false
            binding.editTitle.isCursorVisible = false

            binding.editBody.isFocusable = false
            binding.editBody.isFocusableInTouchMode = false
            binding.editBody.isCursorVisible = false
            binding.editBody.movementMethod = LinkMovementMethod.getInstance()

            // Apply clickable links
            Linkify.addLinks(binding.editBody, Linkify.ALL)

            // Tapping on body outside links enables Edit Mode
            binding.editBody.setOnClickListener {
                setEditMode(true)
            }
        }
        invalidateOptionsMenu()
    }

    private fun setupTextWatchersForUndo() {
        val watcher = object : TextWatcher {
            private var beforeSnapshot: TextSnapshot? = null

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!isUndoOrRedoAction && textWatcherActive && isEditMode) {
                    beforeSnapshot = TextSnapshot(
                        title = binding.editTitle.text.toString(),
                        body = binding.editBody.text.toString()
                    )
                }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (!isUndoOrRedoAction && textWatcherActive && isEditMode) {
                    beforeSnapshot?.let { prev ->
                        val current = TextSnapshot(
                            title = binding.editTitle.text.toString(),
                            body = binding.editBody.text.toString()
                        )
                        if (prev != current) {
                            undoStack.push(prev)
                            if (undoStack.size > 50) undoStack.removeLast()
                            redoStack.clear()
                            invalidateOptionsMenu()
                        }
                    }
                }
            }
        }

        binding.editTitle.addTextChangedListener(watcher)
        binding.editBody.addTextChangedListener(watcher)
    }

    private fun performUndo() {
        if (undoStack.isNotEmpty()) {
            val currentState = TextSnapshot(
                title = binding.editTitle.text.toString(),
                body = binding.editBody.text.toString()
            )
            redoStack.push(currentState)

            val prevState = undoStack.pop()
            isUndoOrRedoAction = true
            binding.editTitle.setText(prevState.title)
            binding.editBody.setText(prevState.body)
            binding.editBody.setSelection(prevState.body.length)
            isUndoOrRedoAction = false
            invalidateOptionsMenu()
        }
    }

    private fun performRedo() {
        if (redoStack.isNotEmpty()) {
            val currentState = TextSnapshot(
                title = binding.editTitle.text.toString(),
                body = binding.editBody.text.toString()
            )
            undoStack.push(currentState)

            val nextState = redoStack.pop()
            isUndoOrRedoAction = true
            binding.editTitle.setText(nextState.title)
            binding.editBody.setText(nextState.body)
            binding.editBody.setSelection(nextState.body.length)
            isUndoOrRedoAction = false
            invalidateOptionsMenu()
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

                textWatcherActive = false
                if (isEncrypted) {
                    if (activePassword != null) {
                        applyDecryptedContent(note, activePassword!!)
                    } else {
                        promptAuthenticationAndLoad(note)
                        return@launch
                    }
                } else {
                    binding.editTitle.setText(note.title)
                    binding.editBody.setText(note.note)
                    setEditMode(false)
                }
                textWatcherActive = true

                invalidateOptionsMenu()
            }
        }
    }

    private fun promptAuthenticationAndLoad(note: NoteEntity) {
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
                        applyDecryptedContent(note, pwd)
                    } else {
                        promptManualPasswordAndLoad(note)
                    }
                },
                onFallbackToPassword = { promptManualPasswordAndLoad(note) },
                onError = { promptManualPasswordAndLoad(note) }
            )
        } else {
            promptManualPasswordAndLoad(note)
        }
    }

    private fun promptManualPasswordAndLoad(note: NoteEntity) {
        val view = layoutInflater.inflate(R.layout.dialog_password, null)
        val editPassword = view.findViewById<EditText>(R.id.edit_password)

        AlertDialog.Builder(this)
            .setTitle(R.string.action_unlock)
            .setView(view)
            .setCancelable(false)
            .setPositiveButton(R.string.action_done) { _, _ ->
                val entered = editPassword.text.toString()
                if (passwordManager.verifyPassword(entered)) {
                    applyDecryptedContent(note, entered)
                } else {
                    Toast.makeText(this, R.string.msg_wrong_password, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton(R.string.action_cancel) { _, _ ->
                finish()
            }
            .show()
    }

    private fun applyDecryptedContent(note: NoteEntity, password: String) {
        activePassword = password
        try {
            textWatcherActive = false
            binding.editTitle.setText(note.title)
            val decryptedBody = CryptoManager.decrypt(note.note, password.toCharArray())
            binding.editBody.setText(decryptedBody)
            textWatcherActive = true
            setEditMode(false)
            invalidateOptionsMenu()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to decrypt note content", Toast.LENGTH_SHORT).show()
            finish()
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
                    Toast.makeText(this, "Note locked with Master Password", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.msg_wrong_password, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun saveNote(onComplete: () -> Unit = {}) {
        if (isEncrypted && activePassword == null) {
            onComplete()
            return
        }

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
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val itemDone = menu.findItem(R.id.action_done)
        val itemEdit = menu.findItem(R.id.action_edit)
        val itemUndo = menu.findItem(R.id.action_undo)
        val itemRedo = menu.findItem(R.id.action_redo)
        val itemLock = menu.findItem(R.id.action_lock)

        if (isEditMode) {
            itemDone?.isVisible = true
            itemEdit?.isVisible = false
            itemUndo?.isVisible = true
            itemRedo?.isVisible = true
            itemUndo?.isEnabled = undoStack.isNotEmpty()
            itemRedo?.isEnabled = redoStack.isNotEmpty()
        } else {
            itemDone?.isVisible = false
            itemEdit?.isVisible = true
            itemUndo?.isVisible = false
            itemRedo?.isVisible = false
        }

        itemLock?.setIcon(if (isEncrypted) R.drawable.ic_lock else R.drawable.ic_lock_open)
        itemLock?.setTitle(if (isEncrypted) R.string.action_unlock else R.string.action_lock)

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                handleBackAction()
                true
            }
            R.id.action_done -> {
                saveNote {
                    setEditMode(false)
                }
                true
            }
            R.id.action_edit -> {
                setEditMode(true)
                true
            }
            R.id.action_undo -> {
                performUndo()
                true
            }
            R.id.action_redo -> {
                performRedo()
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
