package com.example.smartskan.data

import android.content.Context
import android.net.Uri
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    suspend fun importScan(pdfUri: Uri, pageCount: Int, firstPageImage: Uri?): ScannedDoc = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.getDefault()).format(Date())
        val doc = ScannedDoc(UUID.randomUUID().toString(), "Scan $stamp", pageCount, System.currentTimeMillis())
        copy(pdfUri, pdfFile(doc))
        firstPageImage?.let { runCatching { copy(it, thumbnailFile(doc)) } }
        documents.update { listOf(doc) + it }
        doc
    }

    /** Creates a new document; [write] fills in the PDF and thumbnail files. */
    suspend fun addDocument(
        name: String,
        pageCount: Int,
        write: suspend (pdf: File, thumbnail: File) -> Unit,
    ): ScannedDoc = withContext(Dispatchers.IO) {
        val doc = ScannedDoc(UUID.randomUUID().toString(), name, pageCount, System.currentTimeMillis())
        try {
            write(pdfFile(doc), thumbnailFile(doc))
        } catch (e: Exception) {
            pdfFile(doc).delete()
            thumbnailFile(doc).delete()
            throw e
        }
        documents.update { listOf(doc) + it }
        doc
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
        val safeName = doc.name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifEmpty { "scan" }
        File(shareDir, "$safeName.pdf").also { pdfFile(doc).copyTo(it, overwrite = true) }
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

    // ---- Codes ----

    fun addCode(code: ScannedCode) = codes.update { listOf(code) + it }
    fun deleteCode(id: String) = codes.update { list -> list.filterNot { it.id == id } }
    fun clearCodes() = codes.update { emptyList() }

    // ---- Expenses ----

    fun saveExpense(expense: Expense) = expenses.update { list ->
        (list.filterNot { it.id == expense.id } + expense).sortedByDescending { it.date }
    }

    fun deleteExpense(id: String) = expenses.update { list -> list.filterNot { it.id == id } }

    private fun copy(uri: Uri, dest: File) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot read $uri")
        input.use { src -> dest.outputStream().use { src.copyTo(it) } }
    }
}
