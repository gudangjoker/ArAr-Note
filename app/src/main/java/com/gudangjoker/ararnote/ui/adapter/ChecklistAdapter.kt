package com.gudangjoker.ararnote.ui.adapter

import android.graphics.Paint
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.gudangjoker.ararnote.R
import com.gudangjoker.ararnote.data.ChecklistItem

class ChecklistAdapter(
    private val items: MutableList<ChecklistItem>,
    private val onItemChanged: () -> Unit
) : RecyclerView.Adapter<ChecklistAdapter.ChecklistViewHolder>() {

    fun getItems(): List<ChecklistItem> = items

    fun addItem(text: String = "") {
        items.add(ChecklistItem(text = text, checked = false))
        notifyItemInserted(items.size - 1)
        onItemChanged()
    }

    fun removeCheckedItems() {
        val iterator = items.iterator()
        var changed = false
        while (iterator.hasNext()) {
            if (iterator.next().checked) {
                iterator.remove()
                changed = true
            }
        }
        if (changed) {
            notifyDataSetChanged()
            onItemChanged()
        }
    }

    fun moveCheckedToBottom() {
        val unchecked = items.filter { !it.checked }
        val checked = items.filter { it.checked }
        items.clear()
        items.addAll(unchecked)
        items.addAll(checked)
        notifyDataSetChanged()
        onItemChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChecklistViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_checklist, parent, false)
        return ChecklistViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChecklistViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ChecklistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: CheckBox = itemView.findViewById(R.id.checkbox_item)
        private val editText: EditText = itemView.findViewById(R.id.edit_item_text)
        private val btnDelete: ImageView = itemView.findViewById(R.id.btn_delete_item)

        private var textWatcher: TextWatcher? = null

        fun bind(item: ChecklistItem) {
            // Remove previous text watcher to prevent recursion
            textWatcher?.let { editText.removeTextChangedListener(it) }

            checkBox.isChecked = item.checked
            editText.setText(item.text)
            updateStrikeThrough(item.checked)

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                item.checked = isChecked
                updateStrikeThrough(isChecked)
                onItemChanged()
            }

            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    item.text = s?.toString() ?: ""
                }
                override fun afterTextChanged(s: Editable?) {
                    onItemChanged()
                }
            }
            editText.addTextChangedListener(textWatcher)

            btnDelete.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    items.removeAt(pos)
                    notifyItemRemoved(pos)
                    onItemChanged()
                }
            }
        }

        private fun updateStrikeThrough(checked: Boolean) {
            if (checked) {
                editText.paintFlags = editText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                editText.alpha = 0.5f
            } else {
                editText.paintFlags = editText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                editText.alpha = 1.0f
            }
        }
    }
}
