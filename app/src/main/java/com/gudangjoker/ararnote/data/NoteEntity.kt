package com.gudangjoker.ararnote.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long = 0L,

    @ColumnInfo(name = "active_state", defaultValue = "0")
    val activeState: Int = ActiveState.ACTIVE,

    @ColumnInfo(name = "account_id", defaultValue = "0")
    val accountId: Int = 0,

    @ColumnInfo(name = "folder_id", defaultValue = "0")
    val folderId: Int = 0,

    @ColumnInfo(name = "status", defaultValue = "0")
    val status: Int = 0,

    @ColumnInfo(name = "space", defaultValue = "0")
    val space: Int = 0,

    @ColumnInfo(name = "type")
    val type: Int = NoteType.TEXT,

    @ColumnInfo(name = "title")
    val title: String = "",

    @ColumnInfo(name = "note")
    val note: String = "",

    @ColumnInfo(name = "note_ext", defaultValue = "''")
    val noteExt: String = "",

    @ColumnInfo(name = "note_type", defaultValue = "0")
    val noteType: Int = 0,

    @ColumnInfo(name = "tags", defaultValue = "''")
    val tags: String = "",

    @ColumnInfo(name = "importance", defaultValue = "0")
    val importance: Int = 0,

    @ColumnInfo(name = "created_date")
    val createdDate: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "modified_date")
    val modifiedDate: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "minor_modified_date", defaultValue = "0")
    val minorModifiedDate: Long = 0L,

    @ColumnInfo(name = "reminder_type", defaultValue = "0")
    val reminderType: Int = 0,

    @ColumnInfo(name = "reminder_option", defaultValue = "0")
    val reminderOption: Int = 0,

    @ColumnInfo(name = "reminder_date", defaultValue = "0")
    val reminderDate: Long = 0L,

    @ColumnInfo(name = "reminder_base", defaultValue = "0")
    val reminderBase: Long = 0L,

    @ColumnInfo(name = "reminder_last", defaultValue = "0")
    val reminderLast: Long = 0L,

    @ColumnInfo(name = "reminder_duration", defaultValue = "0")
    val reminderDuration: Long = 0L,

    @ColumnInfo(name = "reminder_repeat", defaultValue = "0")
    val reminderRepeat: Int = 0,

    @ColumnInfo(name = "reminder_repeat_ends", defaultValue = "0")
    val reminderRepeatEnds: Long = 0L,

    @ColumnInfo(name = "latitude", defaultValue = "0")
    val latitude: Double = 0.0,

    @ColumnInfo(name = "longitude", defaultValue = "0")
    val longitude: Double = 0.0,

    @ColumnInfo(name = "color_index")
    val colorIndex: Int = NoteColor.YELLOW.index,

    @ColumnInfo(name = "encrypted", defaultValue = "0")
    val encrypted: Int = 0,

    @ColumnInfo(name = "dirty", defaultValue = "1")
    val dirty: Int = 1,

    @ColumnInfo(name = "staged", defaultValue = "0")
    val staged: Int = 0,

    @ColumnInfo(name = "uuid")
    val uuid: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "revision", defaultValue = "0")
    val revision: Int = 0
)
