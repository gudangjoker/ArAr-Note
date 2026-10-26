package com.gudangjoker.ararnote.backup

import android.content.Context
import com.gudangjoker.ararnote.data.AppDatabase
import com.gudangjoker.ararnote.data.NoteEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupManager(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)

    suspend fun createBackupJson(): Pair<String, Int> {
        val notes = db.noteDao().getAllNotesForBackup()
        val rootObj = JSONObject()
        rootObj.put("app", "ArAr Note")
        rootObj.put("version", 1)
        rootObj.put("timestamp", System.currentTimeMillis())

        val notesArray = JSONArray()
        for (n in notes) {
            val obj = JSONObject()
            obj.put("_id", n.id)
            obj.put("active_state", n.activeState)
            obj.put("account_id", n.accountId)
            obj.put("folder_id", n.folderId)
            obj.put("status", n.status)
            obj.put("space", n.space)
            obj.put("type", n.type)
            obj.put("title", n.title)
            obj.put("note", n.note)
            obj.put("note_ext", n.noteExt)
            obj.put("note_type", n.noteType)
            obj.put("tags", n.tags)
            obj.put("importance", n.importance)
            obj.put("created_date", n.createdDate)
            obj.put("modified_date", n.modifiedDate)
            obj.put("reminder_type", n.reminderType)
            obj.put("reminder_date", n.reminderDate)
            obj.put("reminder_repeat", n.reminderRepeat)
            obj.put("color_index", n.colorIndex)
            obj.put("encrypted", n.encrypted)
            obj.put("uuid", n.uuid)
            notesArray.put(obj)
        }
        rootObj.put("notes", notesArray)
        return Pair(rootObj.toString(2), notes.size)
    }

    suspend fun writeBackupToStream(outputStream: OutputStream): Int {
        val (jsonString, count) = createBackupJson()
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(jsonString)
            writer.flush()
        }
        return count
    }

    suspend fun createBackup(): File {
        val (jsonString, _) = createBackupJson()

        val backupDir = File(context.getExternalFilesDir(null), "backup")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }

        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val fileName = "ArArNote_${dateFormat.format(Date())}.backup"
        val backupFile = File(backupDir, fileName)

        FileOutputStream(backupFile).use { fos ->
            fos.write(jsonString.toByteArray(Charsets.UTF_8))
        }

        return backupFile
    }

    suspend fun restoreBackup(inputStream: InputStream): Int {
        val content = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val rootObj = JSONObject(content)
        val notesArray = rootObj.getJSONArray("notes")
        val restoredNotes = mutableListOf<NoteEntity>()

        for (i in 0 until notesArray.length()) {
            val obj = notesArray.getJSONObject(i)
            val note = NoteEntity(
                id = obj.optLong("_id", 0L),
                activeState = obj.optInt("active_state", 0),
                accountId = obj.optInt("account_id", 0),
                folderId = obj.optInt("folder_id", 0),
                status = obj.optInt("status", 0),
                space = obj.optInt("space", 0),
                type = obj.optInt("type", 0),
                title = obj.optString("title", ""),
                note = obj.optString("note", ""),
                noteExt = obj.optString("note_ext", ""),
                noteType = obj.optInt("note_type", 0),
                tags = obj.optString("tags", ""),
                importance = obj.optInt("importance", 0),
                createdDate = obj.optLong("created_date", System.currentTimeMillis()),
                modifiedDate = obj.optLong("modified_date", System.currentTimeMillis()),
                reminderType = obj.optInt("reminder_type", 0),
                reminderDate = obj.optLong("reminder_date", 0L),
                reminderRepeat = obj.optInt("reminder_repeat", 0),
                colorIndex = obj.optInt("color_index", 0),
                encrypted = obj.optInt("encrypted", 0),
                uuid = obj.optString("uuid", java.util.UUID.randomUUID().toString())
            )
            restoredNotes.add(note)
        }

        db.noteDao().insertAll(restoredNotes)
        return restoredNotes.size
    }

    suspend fun restoreBackup(file: File): Int {
        return FileInputStream(file).use { restoreBackup(it) }
    }
}
