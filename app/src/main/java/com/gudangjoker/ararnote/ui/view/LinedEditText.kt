package com.gudangjoker.ararnote.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import com.gudangjoker.ararnote.R

class LinedEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    private val lineRect = Rect()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        var lineColor = 0x25000000
        var strokeWidth = 2f

        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.LinedEditText, defStyleAttr, 0)
            lineColor = a.getColor(R.styleable.LinedEditText_lineColor, lineColor)
            strokeWidth = a.getDimension(R.styleable.LinedEditText_lineStrokeWidth, strokeWidth)
            a.recycle()
        }

        linePaint.style = Paint.Style.STROKE
        linePaint.color = lineColor
        linePaint.strokeWidth = strokeWidth
    }

    fun setLineColor(color: Int) {
        linePaint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val count = lineCount
        val r = lineRect
        val paint = linePaint
        val currentLineHeight = lineHeight
        val lineOffset = (4f * resources.displayMetrics.density)

        val clip = canvas.clipBounds
        val startLine = if (currentLineHeight > 0) kotlin.math.max(0, (clip.top - paddingTop) / currentLineHeight) else 0
        val endLine = if (currentLineHeight > 0) kotlin.math.min(count, (clip.bottom - paddingTop) / currentLineHeight + 2) else count

        // Draw ruled lines only for visible text lines (clipped viewport)
        for (i in startLine until endLine) {
            val baseline = getLineBounds(i, r)
            canvas.drawLine(
                r.left.toFloat(),
                baseline + lineOffset,
                r.right.toFloat(),
                baseline + lineOffset,
                paint
            )
        }

        // Draw continuing ruled lines all the way to the bottom of the page
        var nextBaseline = if (count > 0) getLineBounds(count - 1, r) + currentLineHeight else r.top + currentLineHeight
        val bottomBound = kotlin.math.max(height - paddingBottom, clip.bottom)
        if (nextBaseline < clip.top && currentLineHeight > 0) {
            val skipped = ((clip.top - nextBaseline) / currentLineHeight) * currentLineHeight
            nextBaseline += skipped
        }
        while (nextBaseline <= bottomBound && nextBaseline <= clip.bottom + currentLineHeight) {
            canvas.drawLine(
                r.left.toFloat(),
                nextBaseline + lineOffset,
                r.right.toFloat(),
                nextBaseline + lineOffset,
                paint
            )
            nextBaseline += currentLineHeight
        }

        super.onDraw(canvas)
    }
}
