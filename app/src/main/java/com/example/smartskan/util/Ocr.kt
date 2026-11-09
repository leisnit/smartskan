package com.example.smartskan.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

/** On-device text recognition (works offline). */
object Ocr {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private suspend fun process(context: Context, uri: Uri): Text {
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
        return recognizer.process(image).await()
    }

    /** Text of a rendered page: one paragraph per recognised block, separated by blank lines. */
    suspend fun readBitmap(bitmap: Bitmap): String =
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            .textBlocks.joinToString("\n\n") { block -> block.lines.joinToString("\n") { it.text } }

    /** Plain text of every image, separated by blank lines. */
    suspend fun readText(context: Context, uris: List<Uri>): String =
        uris.map { process(context, it).text.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")

    /**
     * Rebuilds visual rows (left-to-right, top-to-bottom). Receipts often have the label and the
     * price in separate text blocks, so plain block order would split "TOTAL" from "12.99".
     */
    suspend fun readRows(context: Context, uri: Uri): List<String> {
        val lines = process(context, uri).textBlocks.flatMap { it.lines }.filter { it.boundingBox != null }
        val rows = mutableListOf<MutableList<Text.Line>>()
        for (line in lines.sortedBy { it.boundingBox!!.centerY() }) {
            val box = line.boundingBox!!
            val row = rows.lastOrNull()
            val ref = row?.first()?.boundingBox
            if (row != null && ref != null && abs(box.centerY() - ref.centerY()) < max(ref.height(), box.height()) / 2) {
                row.add(line)
            } else {
                rows.add(mutableListOf(line))
            }
        }
        return rows.map { row -> row.sortedBy { it.boundingBox!!.left }.joinToString(" ") { it.text } }
    }
}
