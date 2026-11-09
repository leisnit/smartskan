package com.example.smartskan.ui.tools

import androidx.compose.ui.res.stringResource
import com.example.smartskan.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import com.example.smartskan.pdf.PdfSource
import com.example.smartskan.pdf.PdfTools
import com.example.smartskan.pdf.WordExport
import com.example.smartskan.ui.ScreenScaffold
import com.example.smartskan.ui.openFile
import com.example.smartskan.ui.shareFile
import com.example.smartskan.ui.toast
import com.example.smartskan.util.pagesLabel
import kotlinx.coroutines.launch
import java.io.File

private class Conversion(val file: File, val result: WordExport.Result)

@Composable
fun ConvertToWordScreen(repo: Repository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val docs by repo.documents.items.collectAsState()
    var source by remember { mutableStateOf<PdfSource?>(null) }
    var pages by remember { mutableStateOf<Int?>(null) }
    var unreadable by remember { mutableStateOf(false) }
    var keepPageBreaks by remember { mutableStateOf(true) }
    var pickingFromApp by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var converting by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<Conversion?>(null) }
    val saveToDevice = rememberSaveToDevice(WordExport.MIME_TYPE)

    fun select(s: PdfSource) {
        source = s
        done = null
        pages = null
        unreadable = false
        scope.launch {
            pages = PdfTools.pageCount(context, s.uri)
            unreadable = pages == null
        }
    }

    val pickDevice = rememberDevicePdfPicker(multiple = false) { uris ->
        uris.firstOrNull()?.let { select(PdfSource(it, displayName(context, it))) }
    }

    fun convert() {
        val s = source ?: return
        converting = true
        progress = null
        scope.launch {
            // Work on a local copy: both PDF engines need a seekable file.
            val input = File(context.cacheDir, "work/convert.pdf")
            try {
                PdfTools.copyToFile(context, s.uri, input)
                val out = repo.exportFile("${s.name}.docx")
                val result = WordExport.convert(context, input, s.name, out, keepPageBreaks) { page, total -> progress = page to total }
                done = Conversion(out, result)
            } catch (e: Exception) {
                toast(context, context.getString(R.string.conversion_failed, e.localizedMessage))
            } finally {
                input.delete()
                converting = false
            }
        }
    }

    ScreenScaffold(title = stringResource(R.string.tool_word), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ToolIntro(
                Icons.AutoMirrored.Outlined.Article,
                stringResource(R.string.word_intro),
            )

            Text(stringResource(R.string.choose_pdf_step), style = MaterialTheme.typography.titleSmall)
            val selected = source
            if (selected == null) {
                PdfSourceButtons(onApp = { pickingFromApp = true }, onDevice = pickDevice, enabled = !converting)
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(selected.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                when {
                                    unreadable -> stringResource(R.string.cant_read_pdf)
                                    else -> pages?.let { pagesLabel(context, it) } ?: stringResource(R.string.reading)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (unreadable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { source = null; done = null }, enabled = !converting) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.choose_other))
                        }
                    }
                }
            }

            Text(stringResource(R.string.options_step), style = MaterialTheme.typography.titleSmall)
            ListItem(
                headlineContent = { Text(stringResource(R.string.keep_breaks)) },
                supportingContent = { Text(stringResource(R.string.keep_breaks_sub)) },
                trailingContent = { Switch(keepPageBreaks, { keepPageBreaks = it }, enabled = !converting) },
            )

            Button(onClick = ::convert, enabled = selected != null && !unreadable && !converting, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (converting) R.string.converting else R.string.convert_to_word))
            }
            if (converting) {
                val p = progress
                if (p == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { p.first / p.second.toFloat() }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.reading_page, p.first, p.second), style = MaterialTheme.typography.bodySmall)
                }
            }

            done?.let { c ->
                val pagesText = pagesLabel(context, c.result.pages)
                val summary = if (c.result.ocrPages > 0) {
                    stringResource(R.string.word_result_ocr, c.result.words, pagesText, c.result.ocrPages)
                } else {
                    stringResource(R.string.word_result, c.result.words, pagesText)
                }
                ResultCard(c.file.name, summary) {
                    ActionRow {
                        SmallOutlinedButton(stringResource(R.string.action_open), Icons.AutoMirrored.Outlined.OpenInNew, {
                            openFile(context, c.file, WordExport.MIME_TYPE)
                        }, Modifier.weight(1f))
                        SmallOutlinedButton(stringResource(R.string.action_share), Icons.Outlined.Share, {
                            shareFile(context, c.file, WordExport.MIME_TYPE)
                        }, Modifier.weight(1f))
                    }
                    SmallOutlinedButton(stringResource(R.string.save_to_device), Icons.Outlined.Download, { saveToDevice(c.file) }, Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (pickingFromApp) {
        DocPickerDialog(
            docs = docs,
            multiSelect = false,
            repo = repo,
            onPicked = { picked -> picked.firstOrNull()?.let { select(it.asSource(repo)) } },
            onDismiss = { pickingFromApp = false },
        )
    }
}
