package com.gudangjoker.ararnote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistItemTest {

    @Test
    fun testJsonSerializationDeserialization() {
        val originalItems = listOf(
            ChecklistItem(text = "Beli Susu", checked = false, id = 101L),
            ChecklistItem(text = "Kirim Laporan", checked = true, id = 102L),
            ChecklistItem(text = "Rapat Tim", checked = false, id = 103L)
        )

        val jsonString = ChecklistItem.toJson(originalItems)
        assertTrue(jsonString.startsWith("[") && jsonString.endsWith("]"))

        val parsedItems = ChecklistItem.fromJson(jsonString)
        assertEquals(3, parsedItems.size)
        assertEquals("Beli Susu", parsedItems[0].text)
        assertFalse(parsedItems[0].checked)
        assertEquals("Kirim Laporan", parsedItems[1].text)
        assertTrue(parsedItems[1].checked)
        assertEquals("Rapat Tim", parsedItems[2].text)
        assertFalse(parsedItems[2].checked)
    }

    @Test
    fun testEmptyJsonReturnsEmptyList() {
        val emptyList = ChecklistItem.fromJson("")
        assertTrue(emptyList.isEmpty())

        val whitespaceList = ChecklistItem.fromJson("   ")
        assertTrue(whitespaceList.isEmpty())
    }

    @Test
    fun testFallbackToPlaintextLines() {
        val plaintext = """
            [x] Item 1 sudah selesai
            [ ] Item 2 belum selesai
            Item 3 tanpa penanda
        """.trimIndent()

        val parsed = ChecklistItem.fromJson(plaintext)
        assertEquals(3, parsed.size)
        assertEquals("Item 1 sudah selesai", parsed[0].text)
        assertTrue(parsed[0].checked)
        assertEquals("Item 2 belum selesai", parsed[1].text)
        assertFalse(parsed[1].checked)
        assertEquals("Item 3 tanpa penanda", parsed[2].text)
        assertFalse(parsed[2].checked)
    }
}
