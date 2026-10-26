package com.gudangjoker.ararnote.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gudangjoker.ararnote.data.ActiveState
import com.gudangjoker.ararnote.data.NoteDao
import com.gudangjoker.ararnote.data.NoteEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

enum class SortOrder {
    MODIFIED,
    CREATED,
    TITLE,
    COLOR,
    REMINDER
}

@OptIn(ExperimentalCoroutinesApi::class)
class NoteViewModel(private val noteDao: NoteDao) : ViewModel() {

    private val _sortOrder = MutableStateFlow(SortOrder.MODIFIED)
    val sortOrder = _sortOrder.asStateFlow()

    private val _colorFilter = MutableStateFlow<Int?>(null)
    val colorFilter = _colorFilter.asStateFlow()

    val activeNotes: Flow<List<NoteEntity>> = kotlinx.coroutines.flow.combine(
        _sortOrder,
        _colorFilter
    ) { sort, filter ->
        Pair(sort, filter)
    }.flatMapLatest { (sort, filter) ->
        if (filter != null) {
            noteDao.getActiveNotesFilteredByColor(filter)
        } else {
            when (sort) {
                SortOrder.MODIFIED -> noteDao.getActiveNotesByModifiedDate()
                SortOrder.CREATED -> noteDao.getActiveNotesByCreatedDate()
                SortOrder.TITLE -> noteDao.getActiveNotesByTitle()
                SortOrder.COLOR -> noteDao.getActiveNotesByColor()
                SortOrder.REMINDER -> noteDao.getActiveNotesByReminder()
            }
        }
    }

    val archivedNotes: Flow<List<NoteEntity>> = noteDao.getArchivedNotes()
    val trashNotes: Flow<List<NoteEntity>> = noteDao.getTrashNotes()

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun setColorFilter(colorIndex: Int?) {
        _colorFilter.value = colorIndex
    }

    fun saveNote(note: NoteEntity, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = noteDao.insertNote(note)
            onSaved(id)
        }
    }

    fun moveToArchive(note: NoteEntity) {
        viewModelScope.launch {
            noteDao.updateNote(note.copy(activeState = ActiveState.ARCHIVED, modifiedDate = System.currentTimeMillis()))
        }
    }

    fun unarchiveNote(note: NoteEntity) {
        viewModelScope.launch {
            noteDao.updateNote(note.copy(activeState = ActiveState.ACTIVE, modifiedDate = System.currentTimeMillis()))
        }
    }

    fun moveToTrash(note: NoteEntity) {
        viewModelScope.launch {
            noteDao.updateNote(note.copy(activeState = ActiveState.TRASH, modifiedDate = System.currentTimeMillis()))
        }
    }

    fun restoreFromTrash(note: NoteEntity) {
        viewModelScope.launch {
            noteDao.updateNote(note.copy(activeState = ActiveState.ACTIVE, modifiedDate = System.currentTimeMillis()))
        }
    }

    fun deletePermanently(note: NoteEntity) {
        viewModelScope.launch {
            noteDao.deleteNote(note)
        }
    }

    fun clearTrash() {
        viewModelScope.launch {
            noteDao.clearTrash()
        }
    }

    fun searchNotes(query: String): Flow<List<NoteEntity>> {
        return noteDao.searchNotes(query)
    }

    suspend fun getNoteById(id: Long): NoteEntity? {
        return noteDao.getNoteById(id)
    }
}

class NoteViewModelFactory(private val noteDao: NoteDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NoteViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NoteViewModel(noteDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
