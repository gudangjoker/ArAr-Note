package com.gudangjoker.ararnote.ui.adapter

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gudangjoker.ararnote.R
import com.gudangjoker.ararnote.data.ChecklistItem
import com.gudangjoker.ararnote.data.NoteColor
import com.gudangjoker.ararnote.data.NoteEntity
import com.gudangjoker.ararnote.data.NoteType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteListAdapter(
    private var isGrid: Boolean = true,
    private val onNoteClick: (NoteEntity) -> Unit,
    private val onNoteLongClick: (NoteEntity) -> Boolean
) : ListAdapter<NoteEntity, NoteListAdapter.NoteViewHolder>(DiffCallback) {

    fun setGridMode(grid: Boolean) {
        if (isGrid != grid) {
            isGrid = grid
            notifyDataSetChanged()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (isGrid) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val layoutRes = if (viewType == VIEW_TYPE_GRID) R.layout.item_note_grid else R.layout.item_note_list
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardNote: CardView = itemView.findViewById(R.id.card_note)
        private val layoutContent: LinearLayout = itemView.findViewById(R.id.layout_content)
        private val textTitle: TextView = itemView.findViewById(R.id.text_title)
        private val textBody: TextView = itemView.findViewById(R.id.text_body)
        private val textDate: TextView = itemView.findViewById(R.id.text_date)
        private val iconPin: ImageView = itemView.findViewById(R.id.icon_pin)
        private val iconLock: ImageView = itemView.findViewById(R.id.icon_lock)
        private val iconReminder: ImageView = itemView.findViewById(R.id.icon_reminder)
        private val indicatorColor: View? = itemView.findViewById(R.id.indicator_color)

        fun bind(note: NoteEntity) {
            val context = itemView.context
            val color = NoteColor.fromIndex(note.colorIndex)
            val noteBgColor = ContextCompat.getColor(context, color.bgResId)
            val headerColor = ContextCompat.getColor(context, color.headerResId)

            if (isGrid) {
                cardNote.setCardBackgroundColor(noteBgColor)
            } else {
                cardNote.setCardBackgroundColor(noteBgColor)
                indicatorColor?.setBackgroundColor(headerColor)
            }

            // Title
            if (note.encrypted == 1) {
                textTitle.text = if (note.title.isNotBlank()) note.title else "🔒 Encrypted Note"
                textBody.text = "Locked content (tap to unlock)"
            } else {
                textTitle.text = note.title.ifBlank { "Untitled" }
                if (note.type == NoteType.CHECKLIST) {
                    val items = ChecklistItem.fromJson(note.note)
                    val completedCount = items.count { it.checked }
                    val totalCount = items.size
                    val progressPrefix = if (totalCount > 0) "✓ $completedCount/$totalCount done\n" else ""
                    val preview = items.take(4).joinToString("\n") { item ->
                        (if (item.checked) "☑ " else "☐ ") + item.text
                    }
                    textBody.text = (progressPrefix + preview).trim().ifBlank { "(Empty checklist)" }
                } else {
                    textBody.text = note.note.ifBlank { "(Empty note)" }
                }
            }

            // Icons
            iconPin.visibility = if (note.importance == 1) View.VISIBLE else View.GONE
            iconLock.visibility = if (note.encrypted == 1) View.VISIBLE else View.GONE
            iconReminder.visibility = if (note.reminderDate > 0) View.VISIBLE else View.GONE

            // Date
            val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            textDate.text = sdf.format(Date(note.modifiedDate))

            itemView.setOnClickListener { onNoteClick(note) }
            itemView.setOnLongClickListener { onNoteLongClick(note) }
        }
    }

    companion object {
        private const val VIEW_TYPE_GRID = 1
        private const val VIEW_TYPE_LIST = 2

        private val DiffCallback = object : DiffUtil.ItemCallback<NoteEntity>() {
            override fun areItemsTheSame(oldItem: NoteEntity, newItem: NoteEntity): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: NoteEntity, newItem: NoteEntity): Boolean {
                return oldItem == newItem
            }
        }
    }
}
