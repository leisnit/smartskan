package com.example.smartskan.ui.tools

import androidx.compose.ui.res.stringResource
import com.example.smartskan.R
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.smartskan.data.ScannedDoc
import com.example.smartskan.data.Repository
import com.example.smartskan.pdf.PdfTools
import com.example.smartskan.ui.ScreenScaffold
import com.example.smartskan.ui.UriThumbnail
import com.example.smartskan.ui.rememberDocumentScanner
import com.example.smartskan.ui.toast
import com.example.smartskan.util.pagesLabel
import com.example.smartskan.util.timestampName
import kotlinx.coroutines.launch

@Composable
fun BookScanScreen(repo: Repository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val namePrefix = stringResource(R.string.name_book)
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var splitSpreads by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf(timestampName(namePrefix)) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var saved by remember { mutableStateOf<ScannedDoc?>(null) }
    val saving = progress != null

    val scan = rememberDocumentScanner(includePdf = false) { result ->
        val pages = result.pages.orEmpty().map { it.imageUri }
        if (pages.isNotEmpty()) {
            photos = pages
            saved = null
            name = timestampName(namePrefix)
        }
    }

    fun save() {
        val images = photos
        val title = name.trim().ifEmpty { timestampName(namePrefix) }
        progress = 0f
        scope.launch {
            try {
                saved = repo.createDocument(title) { pdf, thumbnail ->
                    PdfTools.imagesToPdf(context, images, pdf, splitSpreads) { done, total -> progress = done / total.toFloat() }
                        .also { PdfTools.writeThumbnail(pdf, thumbnail) }
                }
                photos = emptyList()
            } catch (e: Exception) {
                toast(context, context.getString(R.string.save_failed, e.localizedMessage))
            } finally {
                progress = null
            }
        }
    }

    ScreenScaffold(title = stringResource(R.string.tool_book), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            saved?.let { doc ->
                ResultCard(stringResource(R.string.saved_doc, doc.name), stringResource(R.string.pages_in_docs, pagesLabel(context, doc.pageCount))) {
                    SavedDocumentActions(doc, repo)
                }
            }

            if (photos.isEmpty()) {
                ToolIntro(
                    Icons.Outlined.AutoStories,
                    stringResource(R.string.book_intro),
                )
            }

            ListItem(
                headlineContent = { Text(stringResource(R.string.split_title)) },
                supportingContent = { Text(stringResource(R.string.split_sub)) },
                trailingContent = { Switch(splitSpreads, { splitSpreads = it }, enabled = !saving) },
            )

            if (photos.isEmpty()) {
                Button(onClick = scan, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CameraAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.scan_pages))
                }
            } else {
                Text(
                    stringResource(R.string.photos_captured, photos.size),
                    style = MaterialTheme.typography.titleSmall,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(photos) { _, uri ->
                        UriThumbnail(uri, Modifier.width(96.dp).aspectRatio(0.75f), contentScale = ContentScale.Fit)
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name_label)) },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = ::save, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (saving) R.string.building_pdf else R.string.save_as_pdf))
                }
                progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                OutlinedButton(onClick = scan, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.discard_rescan))
                }
            }
        }
    }
}
