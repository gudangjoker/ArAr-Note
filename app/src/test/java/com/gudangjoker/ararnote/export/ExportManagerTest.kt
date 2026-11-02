package com.gudangjoker.ararnote.export

import com.gudangjoker.ararnote.data.ChecklistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportManagerTest {

    @Test
    fun testSanitizeFilename() {
        assertEquals("My_Document_2026", ExportManager.sanitizeFilename("My Document 2026"))
        assertEquals("File_With_Special_Chars", ExportManager.sanitizeFilename("File! With @Special #Chars?"))
        assertEquals("Note", ExportManager.sanitizeFilename("???///:::"))
        assertEquals("Note", ExportManager.sanitizeFilename("   "))
    }

    @Test
    fun testGenerateTxtForTextNote() {
        val title = "Catatan Penting"
        val body = "Ini isi teks catatan."
        val output = ExportManager.generateTxt(
            title = title,
            body = body,
            isChecklist = false,
            checklistItems = emptyList(),
            createdDate = 1700000000000L,
            modifiedDate = 1700001000000L
        )

        assertTrue(output.contains("Catatan Penting"))
        assertTrue(output.contains("Ini isi teks catatan."))
        assertTrue(output.contains("Exported from ArAr Note"))
    }

    @Test
    fun testGenerateTxtForChecklist() {
        val items = listOf(
            ChecklistItem(text = "Tugas 1", checked = true),
            ChecklistItem(text = "Tugas 2", checked = false)
        )
        val output = ExportManager.generateTxt(
            title = "Daftar Tugas",
            body = "",
            isChecklist = true,
            checklistItems = items,
            createdDate = 0L,
            modifiedDate = 0L
        )

        assertTrue(output.contains("Daftar Tugas"))
        assertTrue(output.contains("[x] Tugas 1"))
        assertTrue(output.contains("[ ] Tugas 2"))
    }

    @Test
    fun testGenerateMarkdownForChecklist() {
        val items = listOf(
            ChecklistItem(text = "Item A", checked = true),
            ChecklistItem(text = "Item B", checked = false)
        )
        val output = ExportManager.generateMarkdown(
            title = "Proyek A",
            body = "",
            isChecklist = true,
            checklistItems = items,
            createdDate = 1700000000000L,
            modifiedDate = 1700001000000L
        )

        assertTrue(output.startsWith("# Proyek A"))
        assertTrue(output.contains("- [x] Item A"))
        assertTrue(output.contains("- [ ] Item B"))
    }
}
