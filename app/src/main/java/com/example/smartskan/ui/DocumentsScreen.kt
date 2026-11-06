package com.example.smartskan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.smartskan.util.formatDateTime
import kotlinx.coroutines.launch

@Composable
fun DocumentsScreen(repo: Repository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val docs by repo.documents.items.collectAsState()
    var saving by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ScannedDoc?>(null) }
    var deleting by remember { mutableStateOf<ScannedDoc?>(null) }
    var annotating by remember { mutableStateOf<ScannedDoc?>(null) }

    val scan = rememberDocumentScanner(includePdf = true) { result ->
        val pdf = result.pdf ?: return@rememberDocumentScanner
        saving = true
        scope.launch {
            try {
                repo.importScan(pdf.uri, pdf.pageCount, result.pages?.firstOrNull()?.imageUri)
                toast(context, "Saved ${pdf.pageCount} page(s) as PDF")
            } catch (e: Exception) {
                toast(context, "Could not save scan: ${e.localizedMessage}")
            } finally {
                saving = false
            }
        }
    }

    ScreenScaffold(
        title = "Documents",
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = scan,
                icon = { Icon(Icons.Filled.DocumentScanner, null) },
                text = { Text("Scan") },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            if (docs.isEmpty()) {
                EmptyState(
                    Icons.Outlined.Description,
                    "No documents yet",
                    "Tap Scan to photograph a document. Edges are detected automatically, and you can crop, " +
                        "rotate and apply filters before saving it as a PDF.",
                    Modifier.padding(padding),
                )
            } else {
                LazyColumn(contentPadding = listPadding(padding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(docs, key = { it.id }) { doc ->
                        DocumentRow(
                            doc = doc,
                            repo = repo,
                            onOpen = { openFile(context, repo.pdfFile(doc), "application/pdf") },
                            onShare = { scope.launch { shareFile(context, repo.exportForShare(doc), "application/pdf") } },
                            onAnnotate = { annotating = doc },
                            onRename = { renaming = doc },
                            onDelete = { deleting = doc },
                        )
                    }
                }
            }
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = padding.calculateTopPadding()))
        }
    }

    renaming?.let { doc ->
        TextInputDialog("Rename document", doc.name, "Rename", { repo.renameDocument(doc, it) }, { renaming = null })
    }
    annotating?.let { doc ->
        PdfEditorDialog(doc, repo, onDismiss = { annotating = null })
    }
    deleting?.let { doc ->
        ConfirmDialog("Delete document?", "\"${doc.name}\" will be permanently deleted.", "Delete",
            { repo.deleteDocument(doc) }, { deleting = null })
    }
}

@Composable
private fun DocumentRow(
    doc: ScannedDoc,
    repo: Repository,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onAnnotate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FileThumbnail(repo.thumbnailFile(doc), Modifier.size(width = 56.dp, height = 72.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(doc.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${doc.pageCount} page${if (doc.pageCount == 1) "" else "s"} · ${formatDateTime(doc.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onAnnotate) { Icon(Icons.Outlined.Gesture, "Sign & annotate") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Open") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                        onClick = { menu = false; onOpen() })
                    DropdownMenuItem(text = { Text("Sign & annotate") }, leadingIcon = { Icon(Icons.Outlined.Gesture, null) },
                        onClick = { menu = false; onAnnotate() })
                    DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Outlined.Share, null) },
                        onClick = { menu = false; onShare() })
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
