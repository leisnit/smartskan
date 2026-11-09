package com.example.smartskan.data

import android.content.Context
import android.net.Uri
import com.example.smartskan.pdf.PdfTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

class Repository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val docsDir = File(context.filesDir, "documents").apply { mkdirs() }
    private val thumbsDir = File(context.filesDir, "thumbnails").apply { mkdirs() }
    private val shareDir = File(context.cacheDir, "shared")

    val documents = JsonListStore(File(context.filesDir, "documents.json"), scope, { it.toJson() }, { ScannedDoc.fromJson(it) })
    val codes = JsonListStore(File(context.filesDir, "codes.json"), scope, { it.toJson() }, { ScannedCode.fromJson(it) })
    val expenses = JsonListStore(File(context.filesDir, "expenses.json"), scope, { it.toJson() }, { Expense.fromJson(it) })

    // ---- Documents ----

    fun pdfFile(doc: ScannedDoc) = File(docsDir, "${doc.id}.pdf")
    fun thumbnailFile(doc: ScannedDoc) = File(thumbsDir, "${doc.id}.jpg")

    suspend fun importScan(name: String, pdfUri: Uri, pageCount: Int, firstPageImage: Uri?): ScannedDoc =
        createDocument(name) { pdf, thumbnail ->
            copy(pdfUri, pdf)
            firstPageImage?.let { runCatching { copy(it, thumbnail) } }
            pageCount
        }

    /** Creates a new document. [write] fills in the PDF and thumbnail files and returns the page count. */
    suspend fun createDocument(
        name: String,
        isBooklet: Boolean = false,
        write: suspend (pdf: File, thumbnail: File) -> Int,
    ): ScannedDoc = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val draft = ScannedDoc(id, name, 0, System.currentTimeMillis(), isBooklet)
        val pages = try {
            write(pdfFile(draft), thumbnailFile(draft))
        } catch (e: Exception) {
            pdfFile(draft).delete()
            thumbnailFile(draft).delete()
            throw e
        }
        val doc = draft.copy(pageCount = pages)
        documents.update { listOf(doc) + it }
        doc
    }

    /** Copies a PDF from anywhere on the device into the app's documents. */
    suspend fun importPdf(uri: Uri, name: String): ScannedDoc = createDocument(name) { pdf, thumbnail ->
        copy(uri, pdf)
        PdfTools.writeThumbnail(pdf, thumbnail)
        PdfTools.pageCount(pdf)
    }

    /** Scratch folder for tool output (e.g. Word exports) that can be shared via the FileProvider. */
    fun exportFile(fileName: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(dir, safeFileName(fileName))
    }

    fun renameDocument(doc: ScannedDoc, name: String) {
        documents.update { list -> list.map { if (it.id == doc.id) it.copy(name = name) else it } }
    }

    fun deleteDocument(doc: ScannedDoc) {
        documents.update { list -> list.filterNot { it.id == doc.id } }
        scope.launch {
            pdfFile(doc).delete()
            thumbnailFile(doc).delete()
        }
    }

    /** Copies the PDF to a share folder under its readable name, so recipients see a nice filename. */
    suspend fun exportForShare(doc: ScannedDoc): File = withContext(Dispatchers.IO) {
        shareDir.mkdirs()
        shareDir.listFiles()?.forEach { it.delete() }
        File(shareDir, "${safeFileName(doc.name)}.pdf").also { pdfFile(doc).copyTo(it, overwrite = true) }
    }

    // ---- Signature ----

    private val signatureFile = File(context.filesDir, "signature.json")
    private val _signature = MutableStateFlow(
        runCatching { Signature.fromJson(JSONObject(signatureFile.readText())) }.getOrNull()
    )
    val signature: StateFlow<Signature?> = _signature.asStateFlow()

    fun saveSignature(signature: Signature) {
        _signature.value = signature
        scope.launch { signatureFile.writeText(signature.toJson().toString()) }
    }

    fun clearSignature() {
        _signature.value = null
        scope.launch { signatureFile.delete() }
    }

    // ---- Storage ----

    /** Bytes used by saved documents and their thumbnails. */
    suspend fun documentStorageBytes(): Long = withContext(Dispatchers.IO) {
        listOf(docsDir, thumbsDir).sumOf { dir -> dir.walk().filter { it.isFile }.sumOf { it.length() } }
    }

    /** Bytes held in the cache (shared copies, exports, temporary files). */
    suspend fun cacheBytes(): Long = withContext(Dispatchers.IO) {
        context.cacheDir.walk().filter { it.isFile }.sumOf { it.length() }
    }

    /** Deletes temporary files (shared copies, exports). Saved documents are untouched. */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    // ---- Codes ----

    fun addCode(code: ScannedCode) = codes.update { listOf(code) + it }
    fun deleteCode(id: String) = codes.update { list -> list.filterNot { it.id == id } }
    fun clearCodes() = codes.update { emptyList() }

    // ---- Expenses ----

    fun saveExpense(expense: Expense) = expenses.update { list ->
        (list.filterNot { it.id == expense.id } + expense).sortedByDescending { it.date }
    }

    fun deleteExpense(id: String) = expenses.update { list -> list.filterNot { it.id == id } }

    private fun safeFileName(name: String) = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifEmpty { "scan" }

    private fun copy(uri: Uri, dest: File) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot read $uri")
        input.use { src -> dest.outputStream().use { src.copyTo(it) } }
    }
}
