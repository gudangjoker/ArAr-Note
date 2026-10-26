package com.gudangjoker.ararnote.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.LayoutInflater
import android.widget.RadioButton
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.gudangjoker.ararnote.R
import com.gudangjoker.ararnote.data.ChecklistItem
import com.gudangjoker.ararnote.data.NoteColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFormat(val extension: String, val mimeType: String) {
    PDF("pdf", "application/pdf"),
    MARKDOWN("md", "text/markdown"),
    TXT("txt", "text/plain")
}

object ExportManager {

    fun sanitizeFilename(name: String): String {
        val clean = name.replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "").trim().replace(Regex("\\s+"), "_")
        return if (clean.isBlank()) "Note" else clean.take(40)
    }

    fun generateTxt(
        title: String,
        body: String,
        isChecklist: Boolean,
        checklistItems: List<ChecklistItem>,
        createdDate: Long,
        modifiedDate: Long
    ): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("==================================================\n")
        sb.append(title.ifBlank { "Untitled Note" }).append("\n")
        sb.append("==================================================\n")
        if (createdDate > 0) {
            sb.append("Created  : ").append(sdf.format(Date(createdDate))).append("\n")
        }
        if (modifiedDate > 0) {
            sb.append("Modified : ").append(sdf.format(Date(modifiedDate))).append("\n")
        }
        sb.append("==================================================\n\n")

        if (isChecklist) {
            for (item in checklistItems) {
                val mark = if (item.checked) "[x] " else "[ ] "
                sb.append(mark).append(item.text).append("\n")
            }
        } else {
            sb.append(body).append("\n")
        }

        sb.append("\n--------------------------------------------------\n")
        sb.append("Exported from ArAr Note\n")
        return sb.toString()
    }

    fun generateMarkdown(
        title: String,
        body: String,
        isChecklist: Boolean,
        checklistItems: List<ChecklistItem>,
        createdDate: Long,
        modifiedDate: Long
    ): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("# ").append(title.ifBlank { "Untitled Note" }).append("\n\n")

        val metadata = mutableListOf<String>()
        if (createdDate > 0) {
            metadata.add("**Created:** ${sdf.format(Date(createdDate))}")
        }
        if (modifiedDate > 0) {
            metadata.add("**Modified:** ${sdf.format(Date(modifiedDate))}")
        }
        if (metadata.isNotEmpty()) {
            sb.append("> ").append(metadata.joinToString(" | ")).append("\n\n")
        }
        sb.append("---\n\n")

        if (isChecklist) {
            for (item in checklistItems) {
                val mark = if (item.checked) "- [x] " else "- [ ] "
                sb.append(mark).append(item.text).append("\n")
            }
        } else {
            sb.append(body).append("\n")
        }

        sb.append("\n---\n")
        sb.append("*Exported with ArAr Note*\n")
        return sb.toString()
    }

    fun generatePdf(
        context: Context,
        outputStream: OutputStream,
        title: String,
        body: String,
        isChecklist: Boolean,
        checklistItems: List<ChecklistItem>,
        createdDate: Long,
        modifiedDate: Long,
        colorIndex: Int
    ) {
        val pdfDocument = PdfDocument()

        // A4 standard points: 595 x 842 pt
        val pageWidth = 595
        val pageHeight = 842
        val marginX = 46f
        val printableWidth = (pageWidth - (marginX * 2)).toInt()
        val topMargin = 46f
        val bottomMargin = 50f
        val maxContentY = pageHeight - bottomMargin

        val noteColor = NoteColor.fromIndex(colorIndex)
        val headerColor = ContextCompat.getColor(context, noteColor.headerResId)

        // Paints
        val brandPaint = Paint().apply {
            color = Color.parseColor("#9CA3AF")
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val titlePaint = TextPaint().apply {
            color = Color.parseColor("#111827")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val metaPaint = Paint().apply {
            color = Color.parseColor("#6B7280")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val dividerPaint = Paint().apply {
            color = Color.parseColor("#E5E7EB")
            strokeWidth = 1f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val textPaint = TextPaint().apply {
            color = Color.parseColor("#1F2937")
            textSize = 12.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val checkedTextPaint = TextPaint().apply {
            color = Color.parseColor("#9CA3AF")
            textSize = 12.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            flags = flags or Paint.STRIKE_THRU_TEXT_FLAG
            isAntiAlias = true
        }

        val accentBarPaint = Paint().apply {
            color = headerColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val checkboxStrokePaint = Paint().apply {
            color = Color.parseColor("#9CA3AF")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            isAntiAlias = true
        }

        val checkboxFillPaint = Paint().apply {
            color = Color.parseColor("#10B981") // Green for checked
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val checkmarkPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        // Function to draw header on page 1
        fun drawFirstPageHeader(): Float {
            // Accent bar at top
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 6f, accentBarPaint)

            var y = topMargin + 10f

            // Brand tag
            canvas.drawText("ARAR NOTE", marginX, y, brandPaint)
            y += 18f

            // Title layout
            val displayTitle = title.ifBlank { "Untitled Note" }
            val titleLayout = StaticLayout.Builder.obtain(displayTitle, 0, displayTitle.length, titlePaint, printableWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(false)
                .build()

            canvas.save()
            canvas.translate(marginX, y)
            titleLayout.draw(canvas)
            canvas.restore()

            y += titleLayout.height + 10f

            // Metadata: Created & Modified
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val metaParts = mutableListOf<String>()
            if (createdDate > 0) metaParts.add("Created: ${sdf.format(Date(createdDate))}")
            if (modifiedDate > 0) metaParts.add("Modified: ${sdf.format(Date(modifiedDate))}")
            if (metaParts.isNotEmpty()) {
                canvas.drawText(metaParts.joinToString("   |   "), marginX, y, metaPaint)
                y += 14f
            }

            // Divider
            canvas.drawLine(marginX, y, marginX + printableWidth, y, dividerPaint)
            y += 16f
            return y
        }

        // Function to draw header on continuation pages
        fun drawContinuationHeader(): Float {
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 4f, accentBarPaint)
            var y = topMargin
            canvas.drawText("${title.ifBlank { "Untitled Note" }} (cont.)", marginX, y, brandPaint)
            y += 10f
            canvas.drawLine(marginX, y, marginX + printableWidth, y, dividerPaint)
            y += 16f
            return y
        }

        // Function to draw footer
        fun drawFooter(c: Canvas, pageNumber: Int) {
            val footerY = pageHeight - 30f
            c.drawLine(marginX, footerY - 10f, marginX + printableWidth, footerY - 10f, dividerPaint)
            c.drawText("Generated by ArAr Note", marginX, footerY, brandPaint)
            val pageText = "Page $pageNumber"
            val textWidth = brandPaint.measureText(pageText)
            c.drawText(pageText, (marginX + printableWidth) - textWidth, footerY, brandPaint)
        }

        var currentY = drawFirstPageHeader()

        if (isChecklist) {
            val checkWidth = 24f
            val itemTextWidth = printableWidth - checkWidth.toInt()

            for (item in checklistItems) {
                val currentTextPaint = if (item.checked) checkedTextPaint else textPaint
                val itemLayout = StaticLayout.Builder.obtain(item.text, 0, item.text.length, currentTextPaint, itemTextWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.2f)
                    .setIncludePad(false)
                    .build()

                val itemTotalHeight = maxOf(16f, itemLayout.height.toFloat()) + 8f

                if (currentY + itemTotalHeight > maxContentY) {
                    drawFooter(canvas, pageNum)
                    pdfDocument.finishPage(page)
                    pageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = drawContinuationHeader()
                }

                // Draw Checkbox
                val boxSize = 13f
                val boxY = currentY + 1.5f
                val boxRect = RectF(marginX, boxY, marginX + boxSize, boxY + boxSize)

                if (item.checked) {
                    canvas.drawRoundRect(boxRect, 3f, 3f, checkboxFillPaint)
                    // Checkmark
                    val path = Path().apply {
                        moveTo(marginX + 2.5f, boxY + 6.5f)
                        lineTo(marginX + 5.5f, boxY + 9.5f)
                        lineTo(marginX + 10.5f, boxY + 3.5f)
                    }
                    canvas.drawPath(path, checkmarkPaint)
                } else {
                    canvas.drawRoundRect(boxRect, 3f, 3f, checkboxStrokePaint)
                }

                // Draw Item Text
                canvas.save()
                canvas.translate(marginX + checkWidth, currentY)
                itemLayout.draw(canvas)
                canvas.restore()

                currentY += itemTotalHeight
            }
        } else {
            // Text note
            val contentLayout = StaticLayout.Builder.obtain(body, 0, body.length, textPaint, printableWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.25f)
                .setIncludePad(false)
                .build()

            for (line in 0 until contentLayout.lineCount) {
                val lineTop = contentLayout.getLineTop(line)
                val lineBottom = contentLayout.getLineBottom(line)
                val lineBaseline = contentLayout.getLineBaseline(line)
                val lineHeight = (lineBottom - lineTop).toFloat()

                if (currentY + lineHeight > maxContentY) {
                    drawFooter(canvas, pageNum)
                    pdfDocument.finishPage(page)
                    pageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = drawContinuationHeader()
                }

                val lineStart = contentLayout.getLineStart(line)
                val lineEnd = contentLayout.getLineEnd(line)
                val lineText = body.substring(lineStart, lineEnd).trimEnd('\n', '\r')
                canvas.drawText(lineText, marginX, currentY + (lineBaseline - lineTop), textPaint)
                currentY += lineHeight
            }
        }

        drawFooter(canvas, pageNum)
        pdfDocument.finishPage(page)

        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
    }

    fun exportToTempFile(
        context: Context,
        format: ExportFormat,
        title: String,
        body: String,
        isChecklist: Boolean,
        checklistItems: List<ChecklistItem>,
        createdDate: Long,
        modifiedDate: Long,
        colorIndex: Int
    ): File {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()
        val safeName = sanitizeFilename(title.ifBlank { "Note" })
        val file = File(exportDir, "${safeName}_${System.currentTimeMillis()}.${format.extension}")

        when (format) {
            ExportFormat.PDF -> {
                FileOutputStream(file).use { out ->
                    generatePdf(context, out, title, body, isChecklist, checklistItems, createdDate, modifiedDate, colorIndex)
                }
            }
            ExportFormat.MARKDOWN -> {
                val md = generateMarkdown(title, body, isChecklist, checklistItems, createdDate, modifiedDate)
                file.writeText(md, Charsets.UTF_8)
            }
            ExportFormat.TXT -> {
                val txt = generateTxt(title, body, isChecklist, checklistItems, createdDate, modifiedDate)
                file.writeText(txt, Charsets.UTF_8)
            }
        }
        return file
    }

    fun saveToDownloads(
        context: Context,
        tempFile: File,
        title: String,
        format: ExportFormat
    ): Uri? {
        val safeName = sanitizeFilename(title.ifBlank { "Note" })
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${safeName}_$timeStamp.${format.extension}"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ArArNote")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    FileInputStream(tempFile).use { input -> input.copyTo(out) }
                }
            }
            uri
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ArArNote")
            if (!dir.exists()) dir.mkdirs()
            val destFile = File(dir, fileName)
            tempFile.copyTo(destFile, overwrite = true)
            Uri.fromFile(destFile)
        }
    }

    fun shareFile(context: Context, file: File, format: ExportFormat, title: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title.ifBlank { "Note" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share note via"))
    }

    fun showExportBottomSheet(
        context: Context,
        title: String,
        body: String,
        isChecklist: Boolean,
        checklistItems: List<ChecklistItem>,
        createdDate: Long,
        modifiedDate: Long,
        colorIndex: Int
    ) {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_export_note, null)

        val cardPdf = view.findViewById<MaterialCardView>(R.id.card_option_pdf)
        val cardMd = view.findViewById<MaterialCardView>(R.id.card_option_markdown)
        val cardTxt = view.findViewById<MaterialCardView>(R.id.card_option_txt)

        val radioPdf = view.findViewById<RadioButton>(R.id.radio_pdf)
        val radioMd = view.findViewById<RadioButton>(R.id.radio_markdown)
        val radioTxt = view.findViewById<RadioButton>(R.id.radio_txt)

        val btnShare = view.findViewById<MaterialButton>(R.id.btn_share_export)
        val btnSave = view.findViewById<MaterialButton>(R.id.btn_save_export)

        var selectedFormat = ExportFormat.PDF

        fun updateSelection(format: ExportFormat) {
            selectedFormat = format
            radioPdf.isChecked = (format == ExportFormat.PDF)
            radioMd.isChecked = (format == ExportFormat.MARKDOWN)
            radioTxt.isChecked = (format == ExportFormat.TXT)

            val primaryColor = ContextCompat.getColor(context, R.color.primary)
            val defaultStrokeColor = Color.parseColor("#E5E7EB")

            cardPdf.strokeColor = if (format == ExportFormat.PDF) primaryColor else defaultStrokeColor
            cardMd.strokeColor = if (format == ExportFormat.MARKDOWN) primaryColor else defaultStrokeColor
            cardTxt.strokeColor = if (format == ExportFormat.TXT) primaryColor else defaultStrokeColor
        }

        updateSelection(ExportFormat.PDF)

        cardPdf.setOnClickListener { updateSelection(ExportFormat.PDF) }
        cardMd.setOnClickListener { updateSelection(ExportFormat.MARKDOWN) }
        cardTxt.setOnClickListener { updateSelection(ExportFormat.TXT) }

        btnShare.setOnClickListener {
            dialog.dismiss()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val file = exportToTempFile(
                        context,
                        selectedFormat,
                        title,
                        body,
                        isChecklist,
                        checklistItems,
                        createdDate,
                        modifiedDate,
                        colorIndex
                    )
                    withContext(Dispatchers.Main) {
                        shareFile(context, file, selectedFormat, title)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "${context.getString(R.string.export_failed)}: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnSave.setOnClickListener {
            dialog.dismiss()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val tempFile = exportToTempFile(
                        context,
                        selectedFormat,
                        title,
                        body,
                        isChecklist,
                        checklistItems,
                        createdDate,
                        modifiedDate,
                        colorIndex
                    )
                    val savedUri = saveToDownloads(context, tempFile, title, selectedFormat)
                    withContext(Dispatchers.Main) {
                        if (savedUri != null) {
                            Toast.makeText(context, context.getString(R.string.export_success, "Downloads/ArArNote"), Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, R.string.export_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "${context.getString(R.string.export_failed)}: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.setContentView(view)
        dialog.show()
    }
}
