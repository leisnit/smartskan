package com.example.smartskan.ui.tools

import androidx.compose.ui.res.stringResource
import com.example.smartskan.R
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.smartskan.data.Repository
import com.example.smartskan.data.ScannedDoc
import com.example.smartskan.pdf.PdfTools
import com.example.smartskan.ui.ScreenScaffold
import com.example.smartskan.ui.TextInputDialog
import com.example.smartskan.ui.UriThumbnail
import com.example.smartskan.ui.copyToClipboard
import com.example.smartskan.ui.rememberDocumentScanner
import com.example.smartskan.ui.toast
import com.example.smartskan.util.Mrz
import com.example.smartskan.util.MrzData
import com.example.smartskan.util.Ocr
import com.example.smartskan.util.timestampName
import kotlinx.coroutines.launch

enum class IdentityKind(
    @StringRes val title: Int,
    @StringRes val docLabel: Int,
    @StringRes val saveTitle: Int,
    val icon: ImageVector,
    val pageLimit: Int,
    val longEdgeMm: Float,
    @StringRes val intro: Int,
    val sideLabels: List<Int>,
) {
    PASSPORT(
        title = R.string.tool_passport,
        docLabel = R.string.label_passport,
        saveTitle = R.string.save_passport,
        icon = Icons.Outlined.Public,
        pageLimit = 1,
        longEdgeMm = PdfTools.PASSPORT_MM,
        intro = R.string.passport_intro,
        sideLabels = listOf(R.string.side_photo),
    ),
    ID_CARD(
        title = R.string.tool_id,
        docLabel = R.string.label_id_card,
        saveTitle = R.string.save_id_card,
        icon = Icons.Outlined.Badge,
        pageLimit = 2,
        longEdgeMm = PdfTools.ID_CARD_MM,
        intro = R.string.id_intro,
        sideLabels = listOf(R.string.side_front, R.string.side_back),
    ),
}

@Composable
fun IdentityScanScreen(kind: IdentityKind, repo: Repository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var mrz by remember { mutableStateOf<MrzData?>(null) }
    var reading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<ScannedDoc?>(null) }

    val scan = rememberDocumentScanner(pageLimit = kind.pageLimit, includePdf = false) { result ->
        val pages = result.pages.orEmpty().map { it.imageUri }
        if (pages.isEmpty()) return@rememberDocumentScanner
        images = pages
        mrz = null
        saved = null
        reading = true
        scope.launch {
            try {
                // The MRZ can be on either side of an ID card; try each until one parses.
                for (uri in pages) {
                    mrz = Mrz.parse(Ocr.readRows(context, uri))
                    if (mrz != null) break
                }
            } catch (_: Exception) {
                // Reading details is a bonus; the scan itself is still usable.
            } finally {
                reading = false
            }
        }
    }

    ScreenScaffold(title = stringResource(kind.title), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (images.isEmpty()) {
                ToolIntro(kind.icon, stringResource(kind.intro))
                Button(onClick = scan, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CameraAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(kind.title))
                }
                return@Column
            }

            // Captured sides
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                images.forEachIndexed { i, uri ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        UriThumbnail(uri, Modifier.fillMaxWidth().aspectRatio(1.4f), contentScale = ContentScale.Fit)
                        Text(
                            kind.sideLabels.getOrNull(i)?.let { stringResource(it) } ?: stringResource(R.string.page_n, i + 1),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (kind == IdentityKind.ID_CARD && images.size == 1) {
                Text(
                    stringResource(R.string.one_side_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Extracted details
            when {
                reading -> {
                    Text(stringResource(R.string.reading_details), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                mrz != null -> MrzCard(mrz!!)
                else -> ToolIntro(
                    Icons.Outlined.ErrorOutline,
                    stringResource(if (kind == IdentityKind.ID_CARD) R.string.no_mrz_id else R.string.no_mrz_passport),
                )
            }

            // Actions
            val doc = saved
            if (doc == null) {
                Button(onClick = { naming = true }, enabled = !saving && !reading, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (saving) R.string.saving else R.string.save_to_documents))
                }
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                ResultCard(stringResource(R.string.saved_doc, doc.name), stringResource(R.string.saved_a4)) {
                    SavedDocumentActions(doc, repo)
                }
            }
            ActionRow {
                mrz?.let { data ->
                    SmallOutlinedButton(stringResource(R.string.copy_details), Icons.Outlined.ContentCopy, {
                        copyToClipboard(context, data.asText(context))
                    }, Modifier.weight(1f))
                }
                OutlinedButton(onClick = scan, enabled = !saving, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.CameraAlt, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.scan_again))
                }
            }
        }
    }

    if (naming) {
        val docLabel = stringResource(kind.docLabel)
        val suggested = mrz?.fullName?.takeIf { it.isNotBlank() }?.let { "$docLabel - $it" }
            ?: timestampName(docLabel)
        TextInputDialog(
            title = stringResource(kind.saveTitle),
            initial = suggested,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { name ->
                val sides = images
                saving = true
                scope.launch {
                    try {
                        saved = repo.createDocument(name) { pdf, thumbnail ->
                            PdfTools.cardsOnA4(context, sides, kind.longEdgeMm, pdf)
                                .also { PdfTools.writeThumbnail(pdf, thumbnail) }
                        }
                    } catch (e: Exception) {
                        toast(context, context.getString(R.string.save_failed, e.localizedMessage))
                    } finally {
                        saving = false
                    }
                }
            },
            onDismiss = { naming = false },
        )
    }
}

@Composable
private fun MrzCard(data: MrzData) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = if (data.verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                Icon(if (data.verified) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null, tint = color)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (data.verified) stringResource(R.string.mrz_verified)
                    else stringResource(R.string.mrz_checks, data.checksPassed, data.checksTotal),
                    style = MaterialTheme.typography.titleSmall,
                    color = color,
                )
            }
            Spacer(Modifier.padding(top = 8.dp))
            data.fields(LocalContext.current).forEachIndexed { i, (label, value) ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.45f),
                    )
                    Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.55f))
                }
            }
        }
    }
}
