package com.gudangjoker.ararnote.data

import org.json.JSONArray
import org.json.JSONObject

data class ChecklistItem(
    var text: String,
    var checked: Boolean = false,
    val id: Long = System.currentTimeMillis()
) {
    companion object {
        fun toJson(items: List<ChecklistItem>): String {
            val jsonArray = JSONArray()
            for (item in items) {
                val obj = JSONObject()
                obj.put("text", item.text)
                obj.put("checked", item.checked)
                obj.put("id", item.id)
                jsonArray.put(obj)
            }
            return jsonArray.toString()
        }

        fun fromJson(json: String): MutableList<ChecklistItem> {
            val list = mutableListOf<ChecklistItem>()
            if (json.isBlank()) return list
            try {
                val jsonArray = JSONArray(json)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        ChecklistItem(
                            text = obj.optString("text", ""),
                            checked = obj.optBoolean("checked", false),
                            id = obj.optLong("id", System.currentTimeMillis() + i)
                        )
                    )
                }
            } catch (e: Exception) {
                // Fallback: newline-delimited text if not JSON
                val lines = json.split("\n")
                for ((index, line) in lines.withIndex()) {
                    if (line.isNotBlank()) {
                        val isChecked = line.startsWith("[x] ") || line.startsWith("[X] ")
                        val cleanText = if (isChecked || line.startsWith("[ ] ")) {
                            line.substring(4)
                        } else {
                            line
                        }
                        list.add(ChecklistItem(text = cleanText, checked = isChecked, id = System.currentTimeMillis() + index))
                    }
                }
            }
            return list
        }
    }
}
