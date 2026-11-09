package com.example.smartskan.ui

import androidx.compose.ui.res.stringResource
import com.example.smartskan.R
import android.app.SearchManager
import androidx.annotation.StringRes
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.smartskan.data.Repository
import com.example.smartskan.data.ScannedCode
import com.example.smartskan.util.formatDateTime
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.UUID

@Composable
fun CodesScreen(repo: Repository, onBack: () -> Unit, scanOnOpen: Boolean, onScanOpened: () -> Unit) {
    val context = LocalContext.current
    val codes by repo.codes.items.collectAsState()
    var selected by remember { mutableStateOf<ScannedCode?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val scan = {
        scanBarcode(context) { barcode ->
            val value = barcode.rawValue ?: barcode.displayValue
            if (value != null) {
                val code = ScannedCode(
                    id = UUID.randomUUID().toString(),
                    value = value,
                    format = formatName(barcode.format),
                    type = typeName(barcode.valueType),
                    createdAt = System.currentTimeMillis(),
                )
                repo.addCode(code)
                selected = code
            }
        }
    }

    // Opened from the Home "Scan QR Code" shortcut: go straight to the scanner, once.
    LaunchedEffect(scanOnOpen) {
        if (scanOnOpen) {
            onScanOpened()
            scan()
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.tool_codes),
        onBack = onBack,
        actions = {
            if (codes.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) { Icon(Icons.Outlined.DeleteSweep, stringResource(R.string.clear_history_desc)) }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = scan, icon = { Icon(Icons.Outlined.QrCodeScanner, null) }, text = { Text(stringResource(R.string.action_scan)) })
        },
    ) { padding ->
        if (codes.isEmpty()) {
            EmptyState(
                Icons.Outlined.QrCode2,
                stringResource(R.string.no_scans),
                stringResource(R.string.no_scans_body),
                Modifier.padding(padding),
            )
        } else {
            LazyColumn(contentPadding = listPadding(padding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(codes, key = { it.id }) { code ->
                    Card(Modifier.fillMaxWidth().clickable { selected = code }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(iconFor(code.type), null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(code.value, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${typeLabel(code.type)} · ${formatLabel(code.format)} · ${formatDateTime(code.createdAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { copyToClipboard(context, code.value) }) {
                                Icon(Icons.Outlined.ContentCopy, stringResource(R.string.action_copy))
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { code ->
        CodeDialog(code, onDelete = { repo.deleteCode(code.id); selected = null }, onDismiss = { selected = null })
    }
    if (confirmClear) {
        ConfirmDialog(stringResource(R.string.clear_history_title), stringResource(R.string.clear_history_body, codes.size), stringResource(R.string.action_clear),
            { repo.clearCodes() }, { confirmClear = false })
    }
}

@Composable
private fun CodeDialog(code: ScannedCode, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val action = remember(code) { primaryAction(code) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(iconFor(code.type), null) },
        title = { Text(typeLabel(code.type)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectionContainer(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    Text(code.value, style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    "${formatLabel(code.format)} · ${formatDateTime(code.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    action?.let { (label, intent) ->
                        AssistChip(
                            onClick = { startSafely(context, intent, context.getString(R.string.no_app_handle)) },
                            label = { Text(stringResource(label)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                        )
                    }
                    AssistChip(onClick = { copyToClipboard(context, code.value) }, label = { Text(stringResource(R.string.action_copy)) },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) })
                    AssistChip(onClick = { shareText(context, code.value) }, label = { Text(stringResource(R.string.action_share)) },
                        leadingIcon = { Icon(Icons.Outlined.Share, null) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        dismissButton = {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
        },
    )
}

/** The most useful thing to do with a code, e.g. open a link or search a product number. */
private fun primaryAction(code: ScannedCode): Pair<Int, Intent>? {
    val v = code.value.trim()
    return when (code.type) {
        TYPE_URL -> R.string.action_open to Intent(Intent.ACTION_VIEW, Uri.parse(if ("://" in v) v else "https://$v"))
        TYPE_PHONE -> R.string.code_call to Intent(Intent.ACTION_DIAL, Uri.parse(if (v.startsWith("tel:", true)) v else "tel:$v"))
        TYPE_EMAIL -> if (v.contains("@") && !v.startsWith("MATMSG", true)) {
            R.string.code_email to Intent(Intent.ACTION_SENDTO, Uri.parse(if (v.startsWith("mailto:", true)) v else "mailto:$v"))
        } else null
        TYPE_GEO -> R.string.code_map to Intent(Intent.ACTION_VIEW, Uri.parse(v))
        TYPE_WIFI, TYPE_CONTACT, TYPE_EVENT, TYPE_SMS -> null
        else -> R.string.code_search to Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, v)
    }
}

// These English names are what gets saved with each scan, so don't change them; typeLabel() is what's shown.
private const val TYPE_URL = "Link"
private const val TYPE_WIFI = "Wi-Fi"
private const val TYPE_CONTACT = "Contact"
private const val TYPE_EMAIL = "Email"
private const val TYPE_PHONE = "Phone"
private const val TYPE_SMS = "SMS"
private const val TYPE_GEO = "Location"
private const val TYPE_EVENT = "Event"
private const val TYPE_PRODUCT = "Product"
private const val TYPE_ISBN = "Book (ISBN)"
private const val TYPE_ID_CARD = "ID card"
private const val TYPE_TEXT = "Text"
private const val FORMAT_QR = "QR Code"
private const val FORMAT_BARCODE = "Barcode"

@Composable
private fun typeLabel(type: String): String {
    @StringRes val label = when (type) {
        TYPE_URL -> R.string.type_link
        TYPE_CONTACT -> R.string.type_contact
        TYPE_EMAIL -> R.string.type_email
        TYPE_PHONE -> R.string.type_phone
        TYPE_GEO -> R.string.type_location
        TYPE_EVENT -> R.string.type_event
        TYPE_PRODUCT -> R.string.type_product
        TYPE_ISBN -> R.string.type_isbn
        TYPE_ID_CARD -> R.string.label_id_card
        TYPE_TEXT -> R.string.type_text
        else -> return type // Wi-Fi and SMS read the same everywhere.
    }
    return stringResource(label)
}

/** Format names such as "EAN-13" are the same in every language. */
@Composable
private fun formatLabel(format: String): String = when (format) {
    FORMAT_QR -> stringResource(R.string.format_qr)
    FORMAT_BARCODE -> stringResource(R.string.format_barcode)
    else -> format
}

private fun typeName(valueType: Int): String = when (valueType) {
    Barcode.TYPE_URL -> TYPE_URL
    Barcode.TYPE_WIFI -> TYPE_WIFI
    Barcode.TYPE_CONTACT_INFO -> TYPE_CONTACT
    Barcode.TYPE_EMAIL -> TYPE_EMAIL
    Barcode.TYPE_PHONE -> TYPE_PHONE
    Barcode.TYPE_SMS -> TYPE_SMS
    Barcode.TYPE_GEO -> TYPE_GEO
    Barcode.TYPE_CALENDAR_EVENT -> TYPE_EVENT
    Barcode.TYPE_PRODUCT -> TYPE_PRODUCT
    Barcode.TYPE_ISBN -> TYPE_ISBN
    Barcode.TYPE_DRIVER_LICENSE -> TYPE_ID_CARD
    else -> TYPE_TEXT
}

private fun formatName(format: Int): String = when (format) {
    Barcode.FORMAT_QR_CODE -> FORMAT_QR
    Barcode.FORMAT_AZTEC -> "Aztec"
    Barcode.FORMAT_CODABAR -> "Codabar"
    Barcode.FORMAT_CODE_39 -> "Code 39"
    Barcode.FORMAT_CODE_93 -> "Code 93"
    Barcode.FORMAT_CODE_128 -> "Code 128"
    Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
    Barcode.FORMAT_EAN_8 -> "EAN-8"
    Barcode.FORMAT_EAN_13 -> "EAN-13"
    Barcode.FORMAT_ITF -> "ITF"
    Barcode.FORMAT_PDF417 -> "PDF417"
    Barcode.FORMAT_UPC_A -> "UPC-A"
    Barcode.FORMAT_UPC_E -> "UPC-E"
    else -> FORMAT_BARCODE
}

private fun iconFor(type: String): ImageVector = when (type) {
    TYPE_URL -> Icons.Outlined.Link
    TYPE_WIFI -> Icons.Outlined.Wifi
    TYPE_CONTACT -> Icons.Outlined.Person
    TYPE_EMAIL -> Icons.Outlined.Email
    TYPE_PHONE -> Icons.Outlined.Phone
    TYPE_SMS -> Icons.Outlined.Sms
    TYPE_GEO -> Icons.Outlined.Place
    TYPE_EVENT -> Icons.Outlined.Event
    TYPE_PRODUCT -> Icons.Outlined.ShoppingCart
    TYPE_ISBN -> Icons.Outlined.Book
    else -> Icons.Outlined.QrCode2
}
