package com.example.smartskan.ui.tools

import androidx.compose.ui.res.stringResource
import com.example.smartskan.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.smartskan.data.Repository
import com.example.smartskan.ui.ScreenScaffold
import com.example.smartskan.util.filesLabel
import kotlinx.coroutines.launch

private class ImportResult(val imported: Int, val failed: Int)

/** Copies PDFs into Documents. Opens the file picker straight away, since that's why you came here. */
@Composable
fun ImportScreen(repo: Repository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ImportResult?>(null) }
    var pickerShown by rememberSaveable { mutableStateOf(false) }

    val pick = rememberDevicePdfPicker(multiple = true) { uris ->
        importing = true
        result = null
        scope.launch {
            val imported = uris.count { uri ->
                runCatching { repo.importPdf(uri, displayName(context, uri)) }.isSuccess
            }
            result = ImportResult(imported, uris.size - imported)
            importing = false
        }
    }
    LaunchedEffect(Unit) {
        if (!pickerShown) {
            pickerShown = true
            pick()
        }
    }

    ScreenScaffold(title = stringResource(R.string.tool_import), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            result?.let { r ->
                val problem = stringResource(R.string.import_problem)
                ResultCard(
                    title = if (r.imported > 0) stringResource(R.string.imported, filesLabel(context, r.imported)) else stringResource(R.string.nothing_imported),
                    subtitle = when {
                        r.failed == 0 -> stringResource(R.string.saved_to_documents)
                        r.imported == 0 -> problem
                        else -> stringResource(R.string.import_partial, filesLabel(context, r.failed), problem)
                    },
                ) {}
            }
            ToolIntro(
                Icons.Outlined.UploadFile,
                stringResource(R.string.import_intro),
            )
            Button(onClick = pick, enabled = !importing, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (importing) R.string.importing else R.string.choose_pdfs))
            }
            if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}
