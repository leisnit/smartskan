package com.example.smartskan.ui.tools

import androidx.compose.ui.res.stringResource
import com.example.smartskan.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Draw
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.smartskan.data.Repository
import com.example.smartskan.data.ScannedDoc
import com.example.smartskan.ui.PdfEditorDialog
import com.example.smartskan.ui.ScreenScaffold
import com.example.smartskan.ui.toast
import kotlinx.coroutines.launch

/** Picks a PDF and opens it in the editor. The editor saves its result as a new document. */
@Composable
fun SignPdfScreen(repo: Repository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val docs by repo.documents.items.collectAsState()
    var pickingFromApp by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ScannedDoc?>(null) }

    val pickDevice = rememberDevicePdfPicker(multiple = false) { uris ->
        val uri = uris.firstOrNull() ?: return@rememberDevicePdfPicker
        importing = true
        scope.launch {
            try {
                editing = repo.importPdf(uri, displayName(context, uri))
            } catch (_: Exception) {
                toast(context, context.getString(R.string.open_failed_damaged))
            } finally {
                importing = false
            }
        }
    }

    ScreenScaffold(title = stringResource(R.string.tool_sign), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ToolIntro(
                Icons.Outlined.Draw,
                stringResource(R.string.sign_intro),
            )
            Text(stringResource(R.string.choose_from), style = MaterialTheme.typography.labelLarge)
            PdfSourceButtons(onApp = { pickingFromApp = true }, onDevice = pickDevice, enabled = !importing)
            if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }

    if (pickingFromApp) {
        DocPickerDialog(
            docs = docs,
            multiSelect = false,
            repo = repo,
            onPicked = { picked -> editing = picked.firstOrNull() },
            onDismiss = { pickingFromApp = false },
        )
    }
    editing?.let { doc -> PdfEditorDialog(doc, repo, onDismiss = { editing = null }) }
}
