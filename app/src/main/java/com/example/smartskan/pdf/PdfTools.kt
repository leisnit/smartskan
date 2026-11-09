package com.example.smartskan.pdf

import com.example.smartskan.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PageLayout
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** A PDF chosen as input for a tool, from the app's documents or from elsewhere on the device. */
data class PdfSource(val uri: Uri, val name: String)

/** PDF building blocks for the Tools screen: merging, image-to-PDF and card layouts. */
object PdfTools {
    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842
    private const val POINTS_PER_MM = 72f / 25.4f

    /** Real-world long edge of an ID-1 card (driving licence, national ID). */
    const val ID_CARD_MM = 85.6f

    /** Real-world long edge of a passport data page (TD3). */
    const val PASSPORT_MM = 125f

    /**
     * Merges [sources] in order into [out]. Pages are copied as-is (no re-rendering), so text,
     * vector content and image quality are preserved. Returns the page count. With [bookLayout],
     * PDF apps that honour it show facing pages with the cover on its own, like a book.
     */
    suspend fun merge(
        context: Context,
        sources: List<PdfSource>,
        out: File,
        bookLayout: Boolean = false,
    ): Int = withContext(Dispatchers.IO) {
        val dest = PDDocument(MemoryUsageSetting.setupTempFileOnly())
        val opened = mutableListOf<PDDocument>()
        try {
            val merger = PDFMergerUtility()
            for (source in sources) {
                val doc = try {
                    val input = context.contentResolver.openInputStream(source.uri)
                        ?: throw IOException(context.getString(R.string.read_named_failed, source.name))
                    input.use { PDDocument.load(it, MemoryUsageSetting.setupTempFileOnly()) }
                } catch (_: InvalidPasswordException) {
                    throw IOException(context.getString(R.string.password_named, source.name))
                } catch (e: IOException) {
                    throw IOException(context.getString(R.string.not_readable_named, source.name, e.localizedMessage), e)
                }
                opened += doc
                merger.appendDocument(dest, doc)
            }
            if (bookLayout) dest.documentCatalog.pageLayout = PageLayout.TWO_PAGE_RIGHT
            dest.save(out)
            dest.numberOfPages
        } finally {
            opened.forEach { runCatching { it.close() } }
            dest.close()
        }
    }

    /**
     * Writes scanned images as PDF pages (A4 width, height following the image). With
     * [splitSpreads], landscape photos of an open book are cut into left and right pages.
     * Returns the page count.
     */
    suspend fun imagesToPdf(
        context: Context,
        images: List<Uri>,
        out: File,
        splitSpreads: Boolean,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Int = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        var pageNumber = 0
        try {
            images.forEachIndexed { index, uri ->
                val bitmap = decodeSampled(context, uri, 2800)
                val halves = if (splitSpreads && bitmap.width > bitmap.height * 1.15f) {
                    val half = bitmap.width / 2
                    listOf(
                        Bitmap.createBitmap(bitmap, 0, 0, half, bitmap.height),
                        Bitmap.createBitmap(bitmap, half, 0, bitmap.width - half, bitmap.height),
                    ).also { bitmap.recycle() }
                } else {
                    listOf(bitmap)
                }
                for (part in halves) {
                    val height = (A4_WIDTH * part.height / part.width.toFloat()).roundToInt().coerceAtLeast(1)
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, height, ++pageNumber).create())
                    page.canvas.drawBitmap(part, null, RectF(0f, 0f, A4_WIDTH.toFloat(), height.toFloat()), paint)
                    doc.finishPage(page)
                    part.recycle()
                }
                onProgress(index + 1, images.size)
            }
            out.outputStream().use { doc.writeTo(it) }
            pageNumber
        } finally {
            doc.close()
        }
    }

    /**
     * Lays out one or two scanned sides (e.g. front and back of an ID card) on a single A4 page
     * at their real-world size, so the PDF prints like a photocopy. [longEdgeMm] is the physical
     * length of the document's longer side.
     */
    suspend fun cardsOnA4(context: Context, sides: List<Uri>, longEdgeMm: Float, out: File): Int =
        withContext(Dispatchers.IO) {
            val doc = PdfDocument()
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            try {
                val longEdge = longEdgeMm * POINTS_PER_MM
                val gap = 36f
                val bitmaps = sides.take(2).map { decodeSampled(context, it, 1800) }
                val boxes = bitmaps.map { bmp ->
                    val aspect = bmp.width / bmp.height.toFloat()
                    if (aspect >= 1f) longEdge to longEdge / aspect else longEdge * aspect to longEdge
                }
                val totalHeight = boxes.sumOf { it.second.toDouble() }.toFloat() + gap * (boxes.size - 1)
                var top = max(48f, (A4_HEIGHT - totalHeight) / 3f)
                val page = doc.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create())
                bitmaps.forEachIndexed { i, bmp ->
                    val (w, h) = boxes[i]
                    val left = (A4_WIDTH - w) / 2f
                    page.canvas.drawBitmap(bmp, null, RectF(left, top, left + w, top + h), paint)
                    top += h + gap
                    bmp.recycle()
                }
                doc.finishPage(page)
                out.outputStream().use { doc.writeTo(it) }
                1
            } finally {
                doc.close()
            }
        }

    /** Renders the first page as a small JPEG for the documents list. Failures are ignored. */
    suspend fun writeThumbnail(pdf: File, thumbnail: File) {
        runCatching {
            val pages = PdfPages.open(pdf)
            try {
                pages.render(0, 360)?.let { bmp ->
                    thumbnail.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                    bmp.recycle()
                }
            } finally {
                pages.close()
            }
        }
    }

    suspend fun pageCount(pdf: File): Int = withContext(Dispatchers.IO) {
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { it.pageCount }
        }
    }

    /** Page count of any PDF Uri, or null if it can't be opened (e.g. password-protected). */
    suspend fun pageCount(context: Context, uri: Uri): Int? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { fd -> PdfRenderer(fd).use { it.pageCount } }
        }.getOrNull()
    }

    /** Copies any PDF Uri to a local file (PdfRenderer and PDFBox both want seekable files). */
    suspend fun copyToFile(context: Context, uri: Uri, dest: File): File = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException(context.getString(R.string.cant_read_file))
        input.use { src -> dest.outputStream().use { src.copyTo(it) } }
        dest
    }

    private fun decodeSampled(context: Context, uri: Uri, maxDimension: Int): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxDimension) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException(context.getString(R.string.cant_read_image))
    }
}
