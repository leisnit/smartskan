package com.example.smartskan.ui

import androidx.compose.ui.res.stringResource
import com.example.smartskan.R
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.example.smartskan.data.LocalAppSettings
import com.example.smartskan.data.ScanMode
import com.example.smartskan.pdf.PdfPages
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import java.io.File

// ---------- Layout ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                    }
                },
                actions = actions,
            )
        },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

/** Content padding for lists: respects the scaffold and leaves room for the FAB. */
fun listPadding(padding: PaddingValues) = PaddingValues(
    start = 16.dp,
    end = 16.dp,
    top = padding.calculateTopPadding() + 8.dp,
    bottom = padding.calculateBottomPadding() + 96.dp,
)

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value, { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()); onDismiss() }) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Search box used wherever a list can be filtered. */
@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Outlined.Close, stringResource(R.string.clear_search)) }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Keeps a PDF open while the caller is on screen, and closes it afterwards. While it is loading,
 * [OpenedPdf.pages] and [OpenedPdf.error] are both null.
 */
class OpenedPdf(val pages: PdfPages? = null, val error: String? = null)

@Composable
fun rememberOpenedPdf(file: File): OpenedPdf {
    val context = LocalContext.current
    var opened by remember(file) { mutableStateOf(OpenedPdf()) }
    LaunchedEffect(file) {
        val pages = try {
            PdfPages.open(file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            opened = OpenedPdf(error = e.localizedMessage ?: context.getString(R.string.open_pdf_failed))
            return@LaunchedEffect
        }
        opened = OpenedPdf(pages)
        try {
            awaitCancellation()
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { pages.close() }
        }
    }
    return opened
}

/** Thumbnail of a document's saved preview image, with a PDF icon until it loads or if there is none. */
@Composable
fun FileThumbnail(file: File, modifier: Modifier = Modifier) {
    UriThumbnail(Uri.fromFile(file), modifier, placeholder = Icons.Outlined.PictureAsPdf)
}

/** Downsampled thumbnail of an image behind a content/file Uri, loaded off the main thread. */
@Composable
fun UriThumbnail(
    uri: Uri,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: ImageVector? = null,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 480 && bounds.outHeight / (sample * 2) >= 480) sample *= 2
                resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(image, null, Modifier.fillMaxSize(), contentScale = contentScale)
        } else if (placeholder != null) {
            Icon(placeholder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------- Scanners ----------

/**
 * Returns a function that opens Google's document scanner (camera, auto-crop, filters, gallery import).
 * No CAMERA permission is required; the UI is provided by Google Play services.
 */
@Composable
fun rememberDocumentScanner(
    pageLimit: Int? = null,
    includePdf: Boolean,
    onResult: (GmsDocumentScanningResult) -> Unit,
): () -> Unit {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val settings = LocalAppSettings.current
    val scanMode by settings.scanMode.collectAsState()
    val galleryImport by settings.galleryImport.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(res.data)?.let(currentOnResult)
        }
    }
    return remember(activity, pageLimit, includePdf, scanMode, galleryImport) {
        {
            val mode = when (scanMode) {
                ScanMode.FULL -> GmsDocumentScannerOptions.SCANNER_MODE_FULL
                ScanMode.FILTERS -> GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER
                ScanMode.BASIC -> GmsDocumentScannerOptions.SCANNER_MODE_BASE
            }
            val builder = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(galleryImport)
                .setScannerMode(mode)
            if (includePdf) {
                builder.setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG, GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            } else {
                builder.setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            }
            pageLimit?.let { builder.setPageLimit(it) }
            if (activity == null) {
                toast(context, context.getString(R.string.scanner_unavailable))
            } else {
                GmsDocumentScanning.getClient(builder.build())
                    .getStartScanIntent(activity)
                    .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
                    .addOnFailureListener { e -> toast(context, context.getString(R.string.scanner_unavailable_detail, e.localizedMessage)) }
            }
        }
    }
}

/** Opens Google's barcode/QR scanner UI. */
fun scanBarcode(context: Context, onResult: (Barcode) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder().enableAutoZoom().build()
    GmsBarcodeScanning.getClient(context, options)
        .startScan()
        .addOnSuccessListener(onResult)
        .addOnFailureListener { e -> toast(context, context.getString(R.string.scanner_unavailable_detail, e.localizedMessage)) }
}

// ---------- Intents ----------

fun toast(context: Context, message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

fun copyToClipboard(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText("SmartSkan", text))
    // Android 13+ shows its own confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) toast(context, context.getString(R.string.copied))
}

fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

fun fileUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

fun shareFile(context: Context, file: File, mimeType: String) {
    val uri = fileUri(context, file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(file.name, uri)
    context.startActivity(Intent.createChooser(send, null))
}

fun openFile(context: Context, file: File, mimeType: String) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(fileUri(context, file), mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startSafely(context, intent, context.getString(R.string.no_app_open_file))
}

fun startSafely(context: Context, intent: Intent, errorMessage: String) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        toast(context, errorMessage)
    }
}
