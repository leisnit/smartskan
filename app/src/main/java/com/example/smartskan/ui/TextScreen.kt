package com.example.smartskan.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.smartskan.util.Ocr
import kotlinx.coroutines.launch

@Composable
fun TextScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun recognize(uris: List<Uri>) {
        if (uris.isEmpty()) return
        busy = true
        scope.launch {
            try {
                val result = Ocr.readText(context, uris)
                if (result.isBlank()) toast(context, "No text found") else text = result
            } catch (e: Exception) {
                toast(context, "Text recognition failed: ${e.localizedMessage}")
            } finally {
                busy = false
            }
        }
    }

    // The document scanner gives a cropped, flattened image, which gives much better OCR than a raw photo.
    val scan = rememberDocumentScanner(pageLimit = 10, includePdf = false) { result ->
        recognize(result.pages.orEmpty().map { it.imageUri })
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { recognize(listOf(it)) }
    }

    ScreenScaffold(
        title = "Text from photo",
        actions = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { copyToClipboard(context, text) }) { Icon(Icons.Outlined.ContentCopy, "Copy") }
                IconButton(onClick = { shareText(context, text) }) { Icon(Icons.Outlined.Share, "Share") }
                IconButton(onClick = { text = "" }) { Icon(Icons.Outlined.Clear, "Clear") }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = scan, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.CameraAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan")
                }
                FilledTonalButton(
                    onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Gallery")
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (text.isEmpty()) {
                EmptyState(
                    Icons.Outlined.TextFields,
                    "Extract text",
                    "Scan pages or pick a photo. The text is recognised on your device, and you can then edit, copy or share it.",
                )
            } else {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Recognised text") },
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 16.dp),
                )
            }
        }
    }
}
