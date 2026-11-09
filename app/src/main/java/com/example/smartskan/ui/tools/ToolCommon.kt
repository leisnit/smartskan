package com.example.smartskan.ui.tools

import androidx.compose.ui.res.stringResource
import com.example.smartskan.R
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.smartskan.data.Repository
import com.example.smartskan.data.ScannedDoc
import com.example.smartskan.pdf.PdfSource
import com.example.smartskan.ui.BookReaderDialog
import com.example.smartskan.ui.FileThumbnail
import com.example.smartskan.ui.openFile
import com.example.smartskan.ui.shareFile
import com.example.smartskan.ui.toast
import com.example.smartskan.util.formatDateTime
import com.example.smartskan.util.pagesLabel
import com.example.smartskan.util.withoutPdfExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------- Naming ----------

/** Human-readable file name of a content Uri, without the .pdf extension. */
fun displayName(context: Context, uri: Uri): String {
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: context.getString(R.string.document_default)
    return name.withoutPdfExtension()
}

fun ScannedDoc.asSource(repo: Repository) = PdfSource(Uri.fromFile(repo.pdfFile(this)), name)

// ---------- Pickers ----------

/** System file picker limited to PDFs; works with any storage provider (Downloads, Drive, ...). */
@Composable
fun rememberDevicePdfPicker(multiple: Boolean, onPicked: (List<Uri>) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    val single = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { callback(listOf(it)) }
    }
    val many = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) callback(uris)
    }
    return remember(multiple) {
        { if (multiple) many.launch(arrayOf("application/pdf")) else single.launch(arrayOf("application/pdf")) }
    }
}

/** Lets the user choose SmartSkan documents (checkboxes, or radio buttons when [multiSelect] is false). */
@Composable
fun DocPickerDialog(
    docs: List<ScannedDoc>,
    multiSelect: Boolean,
    repo: Repository,
    onPicked: (List<ScannedDoc>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val selected = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (multiSelect) R.string.choose_documents else R.string.choose_document)) },
        text = {
            if (docs.isEmpty()) {
                Text(stringResource(R.string.no_saved_docs))
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(docs, key = { it.id }) { doc ->
                        val checked = doc.id in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (multiSelect) {
                                        if (checked) selected.remove(doc.id) else selected.add(doc.id)
                                    } else {
                                        selected.clear(); selected.add(doc.id)
                                    }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (multiSelect) Checkbox(checked, null) else RadioButton(checked, null)
                            Spacer(Modifier.width(8.dp))
                            FileThumbnail(repo.thumbnailFile(doc), Modifier.size(width = 36.dp, height = 46.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${pagesLabel(context, doc.pageCount)} · ${formatDateTime(doc.createdAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = {
                    // Keep the order of the list for multi-select; selection order would be surprising.
                    onPicked(docs.filter { it.id in selected })
                    onDismiss()
                },
            ) {
                Text(if (multiSelect && selected.size > 1) stringResource(R.string.add_n, selected.size) else stringResource(R.string.action_select))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}


/** "From SmartSkan" / "From device" button pair used by tools that take PDFs as input. */
@Composable
fun PdfSourceButtons(onApp: () -> Unit, onDevice: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalButton(onClick = onApp, enabled = enabled, modifier = Modifier.weight(1f)) {
            Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("SmartSkan")
        }
        FilledTonalButton(onClick = onDevice, enabled = enabled, modifier = Modifier.weight(1f)) {
            Icon(Icons.Outlined.PhoneAndroid, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.from_device))
        }
    }
}

// ---------- Cards ----------

/** Short explanation shown at the top of a tool. */
@Composable
fun ToolIntro(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** Card announcing a finished result, with action buttons underneath. */
@Composable
fun ResultCard(title: String, subtitle: String, modifier: Modifier = Modifier, actions: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            actions()
        }
    }
}

/** Open and Share buttons for a document a tool just saved. Booklets open in the flip-through reader. */
@Composable
fun SavedDocumentActions(doc: ScannedDoc, repo: Repository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    ActionRow {
        if (doc.isBooklet) {
            SmallOutlinedButton(stringResource(R.string.flip_through), Icons.AutoMirrored.Outlined.MenuBook, { reading = true }, Modifier.weight(1f))
        } else {
            SmallOutlinedButton(stringResource(R.string.action_open), Icons.AutoMirrored.Outlined.OpenInNew, {
                openFile(context, repo.pdfFile(doc), "application/pdf")
            }, Modifier.weight(1f))
        }
        SmallOutlinedButton(stringResource(R.string.action_share), Icons.Outlined.Share, {
            scope.launch { shareFile(context, repo.exportForShare(doc), "application/pdf") }
        }, Modifier.weight(1f))
    }
    if (reading) BookReaderDialog(doc, repo, onDismiss = { reading = false })
}

@Composable
fun ActionRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
fun SmallOutlinedButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1)
    }
}

// ---------- Saving ----------

/** Returns a function that asks where to save [File] on the device (Downloads, Drive, ...) and copies it there. */
@Composable
fun rememberSaveToDevice(mimeType: String): (File) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<File?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
        val file = pending
        pending = null
        if (uri == null || file == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                        ?: error("no stream")
                }.isSuccess
            }
            toast(context, context.getString(if (ok) R.string.saved else R.string.save_file_failed))
        }
    }
    return { file ->
        pending = file
        launcher.launch(file.name)
    }
}
