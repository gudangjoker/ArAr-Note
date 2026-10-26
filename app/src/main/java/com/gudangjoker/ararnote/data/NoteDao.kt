package com.gudangjoker.ararnote.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<NoteEntity>)

    @Update
    suspend fun updateNote(note: NoteEntity): Int

    @Delete
    suspend fun deleteNote(note: NoteEntity): Int

    @Query("DELETE FROM notes WHERE _id = :id")
    suspend fun deleteNoteById(id: Long): Int

    @Query("SELECT * FROM notes WHERE _id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE _id = :id LIMIT 1")
    fun getNoteByIdFlow(id: Long): Flow<NoteEntity?>

    // Active Notes with Sort Variations
    @Query("SELECT * FROM notes WHERE active_state = 0 ORDER BY importance DESC, modified_date DESC")
    fun getActiveNotesByModifiedDate(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE active_state = 0 ORDER BY importance DESC, created_date DESC")
    fun getActiveNotesByCreatedDate(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE active_state = 0 ORDER BY importance DESC, title COLLATE NOCASE ASC")
    fun getActiveNotesByTitle(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE active_state = 0 ORDER BY importance DESC, color_index ASC, modified_date DESC")
    fun getActiveNotesByColor(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE active_state = 0 AND reminder_date > 0 ORDER BY reminder_date ASC")
    fun getActiveNotesByReminder(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE active_state = 0 AND color_index = :colorIndex ORDER BY importance DESC, modified_date DESC")
    fun getActiveNotesFilteredByColor(colorIndex: Int): Flow<List<NoteEntity>>

    // Archive and Trash
    @Query("SELECT * FROM notes WHERE active_state = 16 ORDER BY modified_date DESC")
    fun getArchivedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE active_state = 32 ORDER BY modified_date DESC")
    fun getTrashNotes(): Flow<List<NoteEntity>>

    @Query("DELETE FROM notes WHERE active_state = 32")
    suspend fun clearTrash(): Int

    // Search
    @Query("SELECT * FROM notes WHERE active_state = 0 AND (title LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%') ORDER BY modified_date DESC")
    fun searchNotes(query: String): Flow<List<NoteEntity>>

    // Backup
    @Query("SELECT * FROM notes ORDER BY _id ASC")
    suspend fun getAllNotesForBackup(): List<NoteEntity>

    // Scheduled Reminders
    @Query("SELECT * FROM notes WHERE active_state = 0 AND reminder_date > :nowTimestamp")
    suspend fun getUpcomingReminders(nowTimestamp: Long): List<NoteEntity>
}
