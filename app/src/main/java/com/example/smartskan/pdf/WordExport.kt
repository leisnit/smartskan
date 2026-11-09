package com.example.smartskan.pdf

import com.example.smartskan.util.Ocr
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.content.Context
import com.example.smartskan.R
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * PDF → Word (.docx). Text is taken from the PDF where it has a text layer; pages without one
 * (scans) are rendered and read with on-device OCR. The result is editable, reflowed text —
 * images and exact layout aren't reproduced.
 */
object WordExport {
    /** Pages with fewer letters/digits than this are treated as scanned images and OCR'd. */
    private const val MIN_TEXT_CHARS = 16

    data class Result(val pages: Int, val ocrPages: Int, val words: Int)

    suspend fun convert(
        context: Context,
        pdf: File,
        title: String,
        out: File,
        keepPageBreaks: Boolean,
        onProgress: (page: Int, total: Int) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        val texts = extractTextLayer(context, pdf).toMutableList()
        var ocrPages = 0
        val needOcr = texts.indices.filter { i -> texts[i].count { it.isLetterOrDigit() } < MIN_TEXT_CHARS }
        if (needOcr.isNotEmpty()) {
            val pages = PdfPages.open(pdf)
            try {
                for (i in texts.indices) {
                    onProgress(i + 1, texts.size)
                    if (i !in needOcr) continue
                    val bitmap = pages.render(i, 2000) ?: continue
                    try {
                        val ocr = Ocr.readBitmap(bitmap)
                        if (ocr.isNotBlank()) {
                            texts[i] = ocr
                            ocrPages++
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
            } finally {
                pages.close()
            }
        } else {
            onProgress(texts.size, texts.size)
        }

        val paragraphs = texts.map(::toParagraphs)
        val words = paragraphs.sumOf { page -> page.sumOf { p -> p.split(' ').count { it.isNotBlank() } } }
        if (words == 0) throw IOException(context.getString(R.string.no_text_pdf))
        writeDocx(title, paragraphs, keepPageBreaks, out)
        Result(texts.size, ocrPages, words)
    }

    private fun extractTextLayer(context: Context, pdf: File): List<String> {
        val doc = try {
            PDDocument.load(pdf, MemoryUsageSetting.setupTempFileOnly())
        } catch (_: InvalidPasswordException) {
            throw IOException(context.getString(R.string.password_pdf))
        }
        return doc.use {
            val stripper = PDFTextStripper().apply {
                setSortByPosition(true)
                setParagraphEnd("\n\n")
            }
            (1..doc.numberOfPages).map { page ->
                stripper.setStartPage(page)
                stripper.setEndPage(page)
                runCatching { stripper.getText(doc) }.getOrDefault("")
            }
        }
    }

    private val listItem = Regex("""^([•●▪◦\-–*]|\d{1,3}[.)]|[a-zA-Z][.)])\s""")

    /** Rejoins wrapped lines into paragraphs; blank lines and list markers start new ones. */
    internal fun toParagraphs(text: String): List<String> {
        val result = mutableListOf<String>()
        for (block in text.replace("\r", "").split(Regex("""\n\s*\n"""))) {
            val current = StringBuilder()
            for (raw in block.lines()) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                when {
                    current.isEmpty() -> current.append(line)
                    listItem.containsMatchIn(line) -> {
                        result += current.toString()
                        current.setLength(0)
                        current.append(line)
                    }
                    // Re-join words hyphenated across a line break.
                    current.endsWith("-") && line.first().isLowerCase() -> {
                        current.setLength(current.length - 1)
                        current.append(line)
                    }
                    else -> current.append(' ').append(line)
                }
            }
            if (current.isNotEmpty()) result += current.toString()
        }
        return result
    }

    // ---------- Minimal WordprocessingML package ----------

    private fun writeDocx(title: String, pages: List<List<String>>, keepPageBreaks: Boolean, out: File) {
        val body = StringBuilder()
        pages.forEachIndexed { index, paragraphs ->
            if (index > 0 && keepPageBreaks) body.append("""<w:p><w:r><w:br w:type="page"/></w:r></w:p>""")
            for (p in paragraphs) {
                body.append("""<w:p><w:r><w:t xml:space="preserve">""").append(xml(p)).append("</w:t></w:r></w:p>")
            }
        }
        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr></w:body></w:document>"""

        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val core = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><dc:title>${xml(title)}</dc:title><dc:creator>SmartSkan</dc:creator><dcterms:created xsi:type="dcterms:W3CDTF">$now</dcterms:created></cp:coreProperties>"""

        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", CONTENT_TYPES)
            entry("_rels/.rels", ROOT_RELS)
            entry("word/_rels/document.xml.rels", DOCUMENT_RELS)
            entry("word/document.xml", document)
            entry("word/styles.xml", STYLES)
            entry("docProps/core.xml", core)
        }
    }

    /** Escapes XML and drops characters that XML 1.0 forbids (PDF text can contain them). */
    private fun xml(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when {
                c == '&' -> sb.append("&amp;")
                c == '<' -> sb.append("&lt;")
                c == '>' -> sb.append("&gt;")
                c == '"' -> sb.append("&quot;")
                c == '\t' -> sb.append(' ')
                c < ' ' || c == '￾' || c == '￿' -> Unit
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/><Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/></Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/></Relationships>"""

    private const val DOCUMENT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:eastAsia="Calibri" w:cs="Calibri"/><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="160" w:line="259" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style></w:styles>"""

    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
}
