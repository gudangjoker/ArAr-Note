package com.gudangjoker.ararnote.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.gudangjoker.ararnote.ArArNoteApp
import com.gudangjoker.ararnote.R
import com.gudangjoker.ararnote.data.ChecklistItem
import com.gudangjoker.ararnote.data.NoteEntity
import com.gudangjoker.ararnote.data.NoteType
import com.gudangjoker.ararnote.databinding.ActivityCalendarBinding
import com.gudangjoker.ararnote.export.ExportManager
import com.gudangjoker.ararnote.security.BiometricHelper
import com.gudangjoker.ararnote.security.CryptoManager
import com.gudangjoker.ararnote.security.PasswordManager
import com.gudangjoker.ararnote.ui.adapter.CalendarDay
import com.gudangjoker.ararnote.ui.adapter.CalendarMonthAdapter
import com.gudangjoker.ararnote.ui.adapter.NoteListAdapter
import com.gudangjoker.ararnote.ui.util.DisplayUtils
import com.gudangjoker.ararnote.ui.viewmodel.NoteViewModel
import com.gudangjoker.ararnote.ui.viewmodel.NoteViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CalendarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalendarBinding
    private lateinit var passwordManager: PasswordManager
    private lateinit var calendarAdapter: CalendarMonthAdapter
    private lateinit var notesAdapter: NoteListAdapter

    private val viewModel: NoteViewModel by viewModels {
        NoteViewModelFactory((application as ArArNoteApp).database.noteDao())
    }

    private val currentViewingMonth = Calendar.getInstance()
    private val selectedDate = Calendar.getInstance()
    private var allRemindersList: List<NoteEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DisplayUtils.optimizeWindowForHighRefreshRate(this)

        binding = ActivityCalendarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        passwordManager = PasswordManager(this)

        setupToolbar()
        setupCalendarGrid()
        setupNotesList()
        setupBottomNav()
        setupFab()
        observeData()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnPrevMonth.setOnClickListener {
            DisplayUtils.triggerHapticFeedback(it)
            currentViewingMonth.add(Calendar.MONTH, -1)
            refreshCalendar()
        }

        binding.btnNextMonth.setOnClickListener {
            DisplayUtils.triggerHapticFeedback(it)
            currentViewingMonth.add(Calendar.MONTH, 1)
            refreshCalendar()
        }

        binding.btnToday.setOnClickListener {
            DisplayUtils.triggerHapticFeedback(it)
            val today = Calendar.getInstance()
            currentViewingMonth.timeInMillis = today.timeInMillis
            selectedDate.timeInMillis = today.timeInMillis
            refreshCalendar()
            loadNotesForSelectedDate()
        }
    }

    private fun setupCalendarGrid() {
        binding.recyclerCalendarGrid.apply {
            layoutManager = GridLayoutManager(this@CalendarActivity, 7)
            setHasFixedSize(true)
            setItemViewCacheSize(35)
        }

        calendarAdapter = CalendarMonthAdapter(emptyList()) { clickedDay ->
            DisplayUtils.triggerHapticFeedback(binding.root)
            selectedDate.timeInMillis = clickedDay.calendar.timeInMillis
            if (!clickedDay.isCurrentMonth) {
                currentViewingMonth.timeInMillis = clickedDay.calendar.timeInMillis
            }
            refreshCalendar()
            loadNotesForSelectedDate()
        }
        binding.recyclerCalendarGrid.adapter = calendarAdapter
    }

    private fun setupNotesList() {
        notesAdapter = NoteListAdapter(
            isGrid = false,
            onNoteClick = { note -> onNoteClicked(note) },
            onNoteLongClick = { note -> showNoteOptionsDialog(note); true }
        )

        binding.recyclerDateNotes.apply {
            layoutManager = LinearLayoutManager(this@CalendarActivity)
            setHasFixedSize(true)
            adapter = notesAdapter
        }
    }

    private fun setupBottomNav() {
        binding.bottomNavigation.selectedItemId = R.id.nav_calendar
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_notes -> {
                    val intent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(intent)
                    finish()
                    true
                }
                R.id.nav_calendar -> true
                R.id.nav_archive -> {
                    startActivity(Intent(this, ArchiveActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_trash -> {
                    startActivity(Intent(this, TrashActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupFab() {
        binding.fabAddDateNote.setOnClickListener {
            DisplayUtils.triggerHapticFeedback(it)
            showAddNoteDialogForSelectedDate()
        }
    }

    private fun showAddNoteDialogForSelectedDate() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_add_note_type, null)

        val btnTextNote = view.findViewById<View>(R.id.btn_type_text)
        val btnChecklist = view.findViewById<View>(R.id.btn_type_checklist)

        btnTextNote.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, TextNoteActivity::class.java).apply {
                putExtra("extra_preset_reminder", selectedDate.timeInMillis)
            }
            startActivity(intent)
        }

        btnChecklist.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, ChecklistNoteActivity::class.java).apply {
                putExtra("extra_preset_reminder", selectedDate.timeInMillis)
            }
            startActivity(intent)
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.allNotesWithReminders.collectLatest { reminders ->
                allRemindersList = reminders
                refreshCalendar()
                loadNotesForSelectedDate()
            }
        }
    }

    private fun refreshCalendar() {
        val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        binding.textMonthYear.text = monthYearFormat.format(currentViewingMonth.time)

        val days = buildDaysForMonth(currentViewingMonth, selectedDate, allRemindersList)
        calendarAdapter.submitDays(days)
    }

    private fun buildDaysForMonth(
        viewingMonth: Calendar,
        selected: Calendar,
        reminders: List<NoteEntity>
    ): List<CalendarDay> {
        val days = mutableListOf<CalendarDay>()

        val cal = Calendar.getInstance().apply {
            timeInMillis = viewingMonth.timeInMillis
            set(Calendar.DAY_OF_MONTH, 1)
        }

        val today = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday, 2 = Monday, etc.

        // Lead days from previous month
        cal.add(Calendar.DAY_OF_MONTH, -(firstDayOfWeek - 1))

        // Total 42 cells (6 rows x 7 days)
        for (i in 0 until 42) {
            val cellDate = Calendar.getInstance().apply { timeInMillis = cal.timeInMillis }
            val isCurrentM = cellDate.get(Calendar.MONTH) == currentMonth
            val isTod = isSameDay(cellDate, today)
            val isSel = isSameDay(cellDate, selected)

            val notesOnThisDay = reminders.filter { note ->
                val noteCal = Calendar.getInstance().apply { timeInMillis = note.reminderDate }
                isSameDay(noteCal, cellDate)
            }

            days.add(
                CalendarDay(
                    calendar = cellDate,
                    isCurrentMonth = isCurrentM,
                    isToday = isTod,
                    isSelected = isSel,
                    notes = notesOnThisDay
                )
            )

            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        return days
    }

    private fun loadNotesForSelectedDate() {
        val titleFormat = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())
        binding.textSelectedDateTitle.text = titleFormat.format(selectedDate.time)

        lifecycleScope.launch {
            viewModel.getNotesForDate(selectedDate.timeInMillis).collectLatest { notes ->
                notesAdapter.submitList(notes)
                binding.textNoteCount.text = "${notes.size} note${if (notes.size != 1) "s" else ""}"
                binding.textEmptyDate.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
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
                        proceedExport(note, note.note)
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
}
