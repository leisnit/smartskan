package com.example.smartskan.ui

import androidx.compose.ui.res.stringResource
import com.example.smartskan.R
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.smartskan.data.Repository
import com.example.smartskan.ui.theme.Accent
import com.example.smartskan.ui.theme.accentTileColors
import com.example.smartskan.ui.tools.BookScanScreen
import com.example.smartskan.ui.tools.CombineMode
import com.example.smartskan.ui.tools.ConvertToWordScreen
import com.example.smartskan.ui.tools.IdentityKind
import com.example.smartskan.ui.tools.IdentityScanScreen
import com.example.smartskan.ui.tools.ImportScreen
import com.example.smartskan.ui.tools.MergeScreen
import com.example.smartskan.ui.tools.SignPdfScreen

enum class ToolGroup(@StringRes val label: Int) {
    SCAN(R.string.group_scan),
    PDF(R.string.group_pdf),
    FINANCE(R.string.group_finance),
}

/** Full-screen tools, opened from Home or from the Tools tab. */
enum class AppTool(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val icon: ImageVector,
    val group: ToolGroup,
    val accent: Color,
) {
    ID_CARD(R.string.tool_id, R.string.tool_id_sub, Icons.Outlined.Badge, ToolGroup.SCAN, Accent.Green),
    PASSPORT(R.string.tool_passport, R.string.tool_passport_sub, Icons.Outlined.Public, ToolGroup.SCAN, Accent.Cyan),
    BOOK(R.string.tool_book, R.string.tool_book_sub, Icons.Outlined.AutoStories, ToolGroup.SCAN, Accent.Amber),
    QR_CODES(R.string.tool_codes, R.string.tool_codes_sub, Icons.Outlined.QrCodeScanner, ToolGroup.SCAN, Accent.Indigo),
    EXTRACT_TEXT(R.string.tool_text, R.string.tool_text_sub, Icons.Outlined.TextFields, ToolGroup.SCAN, Accent.Purple),
    IMPORT_FILES(R.string.tool_import, R.string.tool_import_sub, Icons.Outlined.UploadFile, ToolGroup.PDF, Accent.Cyan),
    SIGN_PDF(R.string.tool_sign, R.string.tool_sign_sub, Icons.Outlined.Draw, ToolGroup.PDF, Accent.Orange),
    MERGE(R.string.tool_merge, R.string.tool_merge_sub, Icons.AutoMirrored.Outlined.MergeType, ToolGroup.PDF, Accent.Teal),
    BOOKLET(R.string.tool_booklet, R.string.tool_booklet_sub, Icons.AutoMirrored.Outlined.MenuBook, ToolGroup.PDF, Accent.Amber),
    CONVERT_WORD(R.string.tool_word, R.string.tool_word_sub, Icons.AutoMirrored.Outlined.Article, ToolGroup.PDF, Accent.Blue),
    SPENDING(R.string.tool_spending, R.string.tool_spending_sub, Icons.Outlined.Receipt, ToolGroup.FINANCE, Accent.Red),
}

/** Shows [tool] full screen. [scanQrOnOpen] makes the QR tool start the camera immediately. */
@Composable
fun ToolHost(tool: AppTool, repo: Repository, scanQrOnOpen: Boolean, onScanQrOpened: () -> Unit, onBack: () -> Unit) {
    when (tool) {
        AppTool.ID_CARD -> IdentityScanScreen(IdentityKind.ID_CARD, repo, onBack)
        AppTool.PASSPORT -> IdentityScanScreen(IdentityKind.PASSPORT, repo, onBack)
        AppTool.BOOK -> BookScanScreen(repo, onBack)
        AppTool.QR_CODES -> CodesScreen(repo, onBack, scanQrOnOpen, onScanQrOpened)
        AppTool.EXTRACT_TEXT -> TextScreen(onBack)
        AppTool.IMPORT_FILES -> ImportScreen(repo, onBack)
        AppTool.SIGN_PDF -> SignPdfScreen(repo, onBack)
        AppTool.MERGE -> MergeScreen(repo, onBack, CombineMode.MERGE)
        AppTool.BOOKLET -> MergeScreen(repo, onBack, CombineMode.BOOKLET)
        AppTool.CONVERT_WORD -> ConvertToWordScreen(repo, onBack)
        AppTool.SPENDING -> SpendingScreen(repo, onBack)
    }
}

/**
 * The Tools tab: every tool, grouped, two per row. Cards in a row share one height, and a group
 * with an odd count ends on a full-width card, so there are no empty gaps.
 */
@Composable
fun ToolsScreen(onOpen: (AppTool) -> Unit) {
    ScreenScaffold(title = stringResource(R.string.tab_tools)) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 48.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ToolGroup.entries.forEach { group ->
                val tools = AppTool.entries.filter { it.group == group }
                if (tools.isEmpty()) return@forEach
                item(key = group.name) {
                    Text(
                        stringResource(group.label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(tools.chunked(2), key = { row -> row.first().name }) { row ->
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (row.size == 1) {
                            WideToolCard(row[0], Modifier.fillMaxWidth()) { onOpen(row[0]) }
                        } else {
                            row.forEach { tool ->
                                ToolCard(tool, Modifier.weight(1f).fillMaxHeight()) { onOpen(tool) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolIcon(tool: AppTool) {
    val (container, content) = accentTileColors(
        tool.accent, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer,
    )
    Surface(shape = RoundedCornerShape(12.dp), color = container, modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(tool.icon, null, tint = content)
        }
    }
}

@Composable
private fun ToolCard(tool: AppTool, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            ToolIcon(tool)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(tool.title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(tool.subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WideToolCard(tool: AppTool, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolIcon(tool)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(tool.title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(tool.subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
