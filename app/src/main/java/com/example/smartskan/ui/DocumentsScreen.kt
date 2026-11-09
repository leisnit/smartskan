package com.example.smartskan.ui

import androidx.compose.ui.res.stringResource
import com.example.smartskan.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.smartskan.data.Repository
import com.example.smartskan.data.ScannedDoc
import com.example.smartskan.util.pagesLabel
import com.example.smartskan.util.timestampName
import com.example.smartskan.util.formatDateTime
import kotlinx.coroutines.launch

/** The Documents tab: every saved document. New scans come from the + button in the bottom bar. */
@Composable
fun DocumentsScreen(repo: Repository) {
    val docs by repo.documents.items.collectAsState()
    val actions = rememberDocumentActions()

    ScreenScaffold(title = stringResource(R.string.tab_documents)) { padding ->
        if (docs.isEmpty()) {
            EmptyState(
                Icons.Outlined.Description,
                stringResource(R.string.no_documents),
                stringResource(R.string.no_documents_body),
                Modifier.padding(padding),
            )
        } else {
            LazyColumn(contentPadding = listPadding(padding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(docs, key = { it.id }) { doc -> DocumentRow(doc, repo, actions) }
            }
        }
    }
    DocumentActionDialogs(actions, repo)
}

// ---------- Smart scan (the + button in the bottom bar) ----------

@Stable
class SmartScan(val launch: () -> Unit, private val savingState: () -> Boolean) {
    val saving get() = savingState()
}

/** Opens the document scanner and saves the result as a PDF in Documents. */
@Composable
fun rememberSmartScan(repo: Repository): SmartScan {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val launch = rememberDocumentScanner(includePdf = true) { result ->
        val pdf = result.pdf ?: return@rememberDocumentScanner
        saving = true
        scope.launch {
            try {
                val name = timestampName(context.getString(R.string.name_scan))
                repo.importScan(name, pdf.uri, pdf.pageCount, result.pages?.firstOrNull()?.imageUri)
                toast(context, context.getString(R.string.saved_pages_pdf, pagesLabel(context, pdf.pageCount)))
            } catch (e: Exception) {
                toast(context, context.getString(R.string.save_scan_failed, e.localizedMessage))
            } finally {
                saving = false
            }
        }
    }
    return remember(launch) { SmartScan(launch) { saving } }
}

// ---------- Document rows and their actions ----------

/** Which document dialog (rename / delete / sign & annotate) is open. */
@Stable
class DocumentActions {
    var renaming by mutableStateOf<ScannedDoc?>(null)
    var deleting by mutableStateOf<ScannedDoc?>(null)
    var annotating by mutableStateOf<ScannedDoc?>(null)
    var reading by mutableStateOf<ScannedDoc?>(null)
}

@Composable
fun rememberDocumentActions() = remember { DocumentActions() }

@Composable
fun DocumentActionDialogs(actions: DocumentActions, repo: Repository) {
    actions.renaming?.let { doc ->
        TextInputDialog(stringResource(R.string.rename_document), doc.name, stringResource(R.string.action_rename), { repo.renameDocument(doc, it) }, { actions.renaming = null })
    }
    actions.annotating?.let { doc ->
        PdfEditorDialog(doc, repo, onDismiss = { actions.annotating = null })
    }
    actions.reading?.let { doc ->
        BookReaderDialog(doc, repo, onDismiss = { actions.reading = null })
    }
    actions.deleting?.let { doc ->
        ConfirmDialog(stringResource(R.string.delete_document_title), stringResource(R.string.delete_document_body, doc.name), stringResource(R.string.action_delete),
            { repo.deleteDocument(doc) }, { actions.deleting = null })
    }
}

@Composable
fun DocumentRow(doc: ScannedDoc, repo: Repository, actions: DocumentActions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val openExternally = { openFile(context, repo.pdfFile(doc), "application/pdf") }
    Card(
        modifier.fillMaxWidth().clickable {
            if (doc.isBooklet) actions.reading = doc else openExternally()
        },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FileThumbnail(repo.thumbnailFile(doc), Modifier.size(width = 56.dp, height = 72.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(doc.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (doc.isBooklet) "${stringResource(R.string.booklet_label)} · " else "") +
                        "${pagesLabel(context, doc.pageCount)} · ${formatDateTime(doc.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { actions.annotating = doc }) { Icon(Icons.Outlined.Gesture, stringResource(R.string.sign_annotate)) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    // Tapping the row opens the document and the pen button signs it, so neither is repeated here.
                    if (doc.isBooklet) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.open_in_pdf_app)) }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                            onClick = { menu = false; openExternally() })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_share)) }, leadingIcon = { Icon(Icons.Outlined.Share, null) },
                        onClick = {
                            menu = false
                            scope.launch { shareFile(context, repo.exportForShare(doc), "application/pdf") }
                        })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = { menu = false; actions.renaming = doc })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { menu = false; actions.deleting = doc })
                }
            }
        }
    }
}
