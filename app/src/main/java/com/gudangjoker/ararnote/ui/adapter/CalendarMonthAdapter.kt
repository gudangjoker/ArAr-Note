package com.gudangjoker.ararnote.ui.adapter

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.gudangjoker.ararnote.R
import com.gudangjoker.ararnote.data.NoteColor
import com.gudangjoker.ararnote.data.NoteEntity
import java.util.Calendar

data class CalendarDay(
    val calendar: Calendar,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    var isSelected: Boolean = false,
    val notes: List<NoteEntity> = emptyList()
)

class CalendarMonthAdapter(
    private var days: List<CalendarDay> = emptyList(),
    private val onDayClick: (CalendarDay) -> Unit
) : RecyclerView.Adapter<CalendarMonthAdapter.DayViewHolder>() {

    fun submitDays(newDays: List<CalendarDay>) {
        days = newDays
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        holder.bind(days[position])
    }

    override fun getItemCount(): Int = days.size

    inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textDayNumber: TextView = itemView.findViewById(R.id.text_day_number)
        private val layoutDots: LinearLayout = itemView.findViewById(R.id.layout_note_dots)

        fun bind(day: CalendarDay) {
            val context = itemView.context
            val dayNumber = day.calendar.get(Calendar.DAY_OF_MONTH)
            textDayNumber.text = dayNumber.toString()

            // Visual Styling for Selection / Today / Normal
            when {
                day.isSelected -> {
                    val shape = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(ContextCompat.getColor(context, R.color.primary))
                    }
                    textDayNumber.background = shape
                    textDayNumber.setTextColor(Color.WHITE)
                }
                day.isToday -> {
                    val shape = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.TRANSPARENT)
                        setStroke(3, ContextCompat.getColor(context, R.color.primary))
                    }
                    textDayNumber.background = shape
                    textDayNumber.setTextColor(ContextCompat.getColor(context, R.color.primary))
                }
                else -> {
                    textDayNumber.background = null
                    if (day.isCurrentMonth) {
                        textDayNumber.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    } else {
                        textDayNumber.setTextColor(Color.parseColor("#BDBDBD"))
                    }
                }
            }

            // Note Color Dots
            layoutDots.removeAllViews()
            if (day.notes.isNotEmpty()) {
                val distinctColors = day.notes.map { it.colorIndex }.distinct().take(3)
                for (colorIndex in distinctColors) {
                    val dot = View(context).apply {
                        val dotShape = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            val c = NoteColor.fromIndex(colorIndex)
                            setColor(ContextCompat.getColor(context, c.headerResId))
                        }
                        background = dotShape
                    }
                    val params = LinearLayout.LayoutParams(12, 12).apply {
                        setMargins(2, 0, 2, 0)
                    }
                    dot.layoutParams = params
                    layoutDots.addView(dot)
                }
            }

            itemView.setOnClickListener {
                onDayClick(day)
            }
        }
    }
}
