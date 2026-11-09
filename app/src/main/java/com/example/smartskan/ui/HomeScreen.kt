package com.example.smartskan.ui

import androidx.compose.ui.res.stringResource
import com.example.smartskan.R
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.smartskan.data.Repository
import com.example.smartskan.ui.theme.LocalColourful
import com.example.smartskan.ui.theme.accentTileColors

private const val RECENT_DOCUMENTS = 5

/** A Home shortcut: a tool's icon and colour, with a label split over two lines to fit the grid. */
private class QuickAction(val tool: AppTool, @StringRes val label: Int)

private val quickActions = listOf(
    QuickAction(AppTool.ID_CARD, R.string.quick_id),
    QuickAction(AppTool.QR_CODES, R.string.quick_qr),
    QuickAction(AppTool.EXTRACT_TEXT, R.string.quick_text),
    QuickAction(AppTool.SIGN_PDF, R.string.quick_sign),
    QuickAction(AppTool.IMPORT_FILES, R.string.quick_import),
    QuickAction(AppTool.MERGE, R.string.quick_merge),
    QuickAction(AppTool.BOOKLET, R.string.quick_booklet),
    QuickAction(AppTool.SPENDING, R.string.quick_spending),
)

/**
 * Start screen: document search, shortcuts to the most used tools and the latest documents.
 * Scanning a new document is the + button, and everything else is in the Documents and Tools tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repo: Repository,
    scanSaving: Boolean,
    onOpenTool: (AppTool) -> Unit,
    onScanQr: () -> Unit,
    onAllDocuments: () -> Unit,
) {
    val docs by repo.documents.items.collectAsState()
    val actions = rememberDocumentActions()
    var query by rememberSaveable { mutableStateOf("") }

    val searching = query.isNotBlank()
    val results = if (searching) docs.filter { it.name.contains(query.trim(), ignoreCase = true) } else emptyList()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("SmartSkan", fontWeight = FontWeight.SemiBold) })
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 56.dp, // room for the centre + button
            ),
        ) {
            item(key = "search") {
                SearchField(query, { query = it }, stringResource(R.string.search_documents), Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            if (scanSaving) {
                item(key = "progress") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) }
            }
            if (searching) {
                item(key = "results-title") { SectionTitle(stringResource(R.string.results)) }
                if (results.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(Icons.Outlined.Search, stringResource(R.string.no_matches), stringResource(R.string.no_matches_body, query.trim()))
                    }
                } else {
                    items(results, key = { it.id }) { doc ->
                        DocumentRow(doc, repo, actions, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                }
            } else {
                item(key = "actions") {
                    QuickActionGrid(
                        onClick = { tool -> if (tool == AppTool.QR_CODES) onScanQr() else onOpenTool(tool) },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                    )
                }
                if (docs.isEmpty()) {
                    item(key = "no-documents") {
                        EmptyState(
                            Icons.Outlined.Description,
                            stringResource(R.string.no_documents),
                            stringResource(R.string.no_documents_home_body),
                        )
                    }
                } else {
                    item(key = "recent-title") {
                        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle(stringResource(R.string.recent_documents), Modifier.weight(1f))
                            TextButton(onClick = onAllDocuments) { Text(stringResource(R.string.see_all)) }
                        }
                    }
                    items(docs.take(RECENT_DOCUMENTS), key = { it.id }) { doc ->
                        DocumentRow(doc, repo, actions, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }

    DocumentActionDialogs(actions, repo)
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

/** Four shortcuts per row: round icon, two-line label underneath. */
@Composable
private fun QuickActionGrid(onClick: (AppTool) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        quickActions.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { action ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onClick(action.tool) }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val colourful = LocalColourful.current
                        val (container, content) = accentTileColors(
                            action.tool.accent, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.primary,
                        )
                        Surface(
                            // Colourful: rounded-square tiles; Classic/Wallpaper: round buttons.
                            shape = if (colourful) RoundedCornerShape(18.dp) else CircleShape,
                            color = container,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(action.tool.icon, null, tint = content)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(action.label),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            minLines = 2,
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
