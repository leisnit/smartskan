package com.example.smartskan.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Page size in PDF points. */
data class PageSize(val width: Int, val height: Int)

/**
 * Renders pages of a PDF. PdfRenderer allows only one open page at a time and isn't thread-safe,
 * so every access goes through a mutex.
 */
class PdfPages private constructor(
    private val fd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val sizes: List<PageSize>,
) {
    private val lock = Mutex()
    private var closed = false

    suspend fun render(index: Int, widthPx: Int): Bitmap? = lock.withLock {
        if (closed) return@withLock null
        withContext(Dispatchers.IO) {
            val page = renderer.openPage(index)
            try {
                val w = widthPx.coerceIn(1, 4000)
                val h = (w * page.height / page.width.toFloat()).roundToInt().coerceAtLeast(1)
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.WHITE)
                    page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                }
            } finally {
                page.close()
            }
        }
    }

    suspend fun close() = lock.withLock {
        if (!closed) {
            closed = true
            renderer.close()
            fd.close()
        }
    }

    /**
     * Writes a new PDF with every page flattened together with its annotations, plus a JPEG
     * thumbnail of the first page. Pages are re-rendered at ~200 dpi, which suits scanned documents.
     */
    suspend fun exportAnnotated(
        annotations: List<List<PdfAnnotation>>,
        out: File,
        thumbnail: File,
    ) = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        try {
            sizes.forEachIndexed { i, size ->
                val bitmap = render(i, (size.width * 2.8f).roundToInt().coerceAtMost(2400)) ?: error("Document closed")
                val page = doc.startPage(PdfDocument.PageInfo.Builder(size.width, size.height, i + 1).create())
                page.canvas.drawBitmap(bitmap, null, RectF(0f, 0f, size.width.toFloat(), size.height.toFloat()), bitmapPaint)
                AnnotationRenderer.draw(page.canvas, annotations.getOrElse(i) { emptyList() }, 1f)
                doc.finishPage(page)
                bitmap.recycle()
            }
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }

        // Thumbnail of the annotated first page
        val first = sizes.first()
        val thumbWidth = 360
        render(0, thumbWidth)?.let { bmp ->
            AnnotationRenderer.draw(android.graphics.Canvas(bmp), annotations.firstOrNull().orEmpty(), thumbWidth / first.width.toFloat())
            thumbnail.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bmp.recycle()
        }
    }

    companion object {
        suspend fun open(file: File): PdfPages = withContext(Dispatchers.IO) {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(fd)
                val sizes = (0 until renderer.pageCount).map { i ->
                    val p = renderer.openPage(i)
                    try { PageSize(p.width, p.height) } finally { p.close() }
                }
                PdfPages(fd, renderer, sizes)
            } catch (e: Exception) {
                fd.close()
                throw e
            }
        }
    }
}
