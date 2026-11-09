package com.example.smartskan.ui.tools

import androidx.compose.ui.res.stringResource
import com.example.smartskan.R
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.smartskan.pdf.PdfTools
import com.example.smartskan.ui.EmptyState
import com.example.smartskan.ui.ScreenScaffold
import com.example.smartskan.ui.TextInputDialog
import com.example.smartskan.ui.toast
import com.example.smartskan.util.filesLabel
import com.example.smartskan.util.pagesLabel
import com.example.smartskan.util.timestampName
import kotlinx.coroutines.launch
import java.util.UUID

private class MergeItem(val source: PdfSource) {
    val key: String = UUID.randomUUID().toString()
    var pages by mutableStateOf<Int?>(null)
    var unreadable by mutableStateOf(false)
}

/** Combining PDFs has one flow with two outcomes: a plain merged PDF, or a booklet to flip through. */
enum class CombineMode(
    @StringRes val title: Int,
    val emptyIcon: ImageVector,
    @StringRes val emptyTitle: Int,
    @StringRes val emptyBody: Int,
    val minFiles: Int,
    @StringRes val addFilesPrompt: Int,
    /** "Merge <files> · <pages>" */
    @StringRes val summary: Int,
    @StringRes val namePrefix: Int,
    @StringRes val nameDialogTitle: Int,
) {
    MERGE(
        title = R.string.tool_merge,
        emptyIcon = Icons.AutoMirrored.Outlined.MergeType,
        emptyTitle = R.string.merge_empty_title,
        emptyBody = R.string.merge_empty_body,
        minFiles = 2,
        addFilesPrompt = R.string.merge_min,
        summary = R.string.merge_summary,
        namePrefix = R.string.name_merged,
        nameDialogTitle = R.string.merge_name_title,
    ),
    BOOKLET(
        title = R.string.tool_booklet,
        emptyIcon = Icons.AutoMirrored.Outlined.MenuBook,
        emptyTitle = R.string.booklet_empty_title,
        emptyBody = R.string.booklet_empty_body,
        minFiles = 1,
        addFilesPrompt = R.string.booklet_min,
        summary = R.string.booklet_summary,
        namePrefix = R.string.name_booklet,
        nameDialogTitle = R.string.booklet_name_title,
    ),
}

@Composable
fun MergeScreen(repo: Repository, onBack: () -> Unit, mode: CombineMode) {
    val booklet = mode == CombineMode.BOOKLET
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val docs by repo.documents.items.collectAsState()
    val items = remember { mutableStateListOf<MergeItem>() }
    var pickingFromApp by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ScannedDoc?>(null) }

    fun add(sources: List<PdfSource>) {
        result = null
        sources.forEach { source ->
            val item = MergeItem(source)
            items += item
            scope.launch {
                val count = PdfTools.pageCount(context, source.uri)
                item.pages = count
                item.unreadable = count == null
            }
        }
    }

    val pickDevice = rememberDevicePdfPicker(multiple = true) { uris: List<Uri> ->
        add(uris.map { PdfSource(it, displayName(context, it)) })
    }

    fun move(from: Int, to: Int) {
        if (to in items.indices) items.add(to, items.removeAt(from))
    }

    ScreenScaffold(title = stringResource(mode.title), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (merging) LinearProgressIndicator(Modifier.fillMaxWidth())
            Box(Modifier.weight(1f)) {
                if (items.isEmpty() && result == null) {
                    EmptyState(mode.emptyIcon, stringResource(mode.emptyTitle), stringResource(mode.emptyBody))
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        result?.let { doc ->
                            item(key = "result") {
                                ResultCard(
                                    stringResource(if (booklet) R.string.created_doc else R.string.merged_doc, doc.name),
                                    stringResource(R.string.pages_saved, pagesLabel(context, doc.pageCount)),
                                ) {
                                    SavedDocumentActions(doc, repo)
                                }
                            }
                        }
                        itemsIndexed(items, key = { _, it -> it.key }) { index, item ->
                            MergeRow(
                                index = index,
                                item = item,
                                isFirst = index == 0,
                                isLast = index == items.lastIndex,
                                enabled = !merging,
                                onUp = { move(index, index - 1) },
                                onDown = { move(index, index + 1) },
                                onRemove = { items.removeAt(index) },
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.add_from), style = MaterialTheme.typography.labelLarge)
                PdfSourceButtons(onApp = { pickingFromApp = true }, onDevice = pickDevice, enabled = !merging)
                val totalPages = items.sumOf { it.pages ?: 0 }
                Button(
                    onClick = { naming = true },
                    enabled = !merging && items.size >= mode.minFiles && items.none { it.unreadable },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            items.any { it.unreadable } -> stringResource(R.string.remove_unreadable)
                            items.size < mode.minFiles -> stringResource(mode.addFilesPrompt)
                            else -> stringResource(mode.summary, filesLabel(context, items.size), pagesLabel(context, totalPages))
                        },
                    )
                }
            }
        }
    }

    if (pickingFromApp) {
        DocPickerDialog(
            docs = docs,
            multiSelect = true,
            repo = repo,
            onPicked = { picked -> add(picked.map { it.asSource(repo) }) },
            onDismiss = { pickingFromApp = false },
        )
    }
    if (naming) {
        TextInputDialog(
            title = stringResource(mode.nameDialogTitle),
            initial = timestampName(stringResource(mode.namePrefix)),
            confirmLabel = stringResource(if (booklet) R.string.action_create else R.string.action_merge),
            onConfirm = { name ->
                val sources = items.map { it.source }
                merging = true
                scope.launch {
                    try {
                        result = repo.createDocument(name, isBooklet = booklet) { pdf, thumbnail ->
                            PdfTools.merge(context, sources, pdf, bookLayout = booklet)
                                .also { PdfTools.writeThumbnail(pdf, thumbnail) }
                        }
                        items.clear()
                    } catch (e: Exception) {
                        toast(context, context.getString(if (booklet) R.string.booklet_failed else R.string.merge_failed, e.localizedMessage))
                    } finally {
                        merging = false
                    }
                }
            },
            onDismiss = { naming = false },
        )
    }
}

@Composable
private fun MergeRow(
    index: Int,
    item: MergeItem,
    isFirst: Boolean,
    isLast: Boolean,
    enabled: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.source.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        item.unreadable -> stringResource(R.string.cant_read_pdf)
                        item.pages == null -> stringResource(R.string.reading)
                        else -> pagesLabel(context, item.pages!!)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.unreadable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onUp, enabled = enabled && !isFirst) { Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.move_up)) }
            IconButton(onClick = onDown, enabled = enabled && !isLast) { Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.move_down)) }
            IconButton(onClick = onRemove, enabled = enabled) { Icon(Icons.Outlined.Close, stringResource(R.string.action_remove)) }
        }
    }
}
