package com.example.smartskan.ui

import androidx.compose.ui.res.stringResource
import com.example.smartskan.R
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.LocalActivity
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.smartskan.ads.LocalAds
import com.example.smartskan.data.AppLanguage
import com.example.smartskan.data.AppLanguages
import com.example.smartskan.data.ColourStyle
import com.example.smartskan.data.LocalAppSettings
import com.example.smartskan.data.LocalPurchases
import com.example.smartskan.data.Purchases
import com.example.smartskan.data.Repository
import com.example.smartskan.data.ScanMode
import com.example.smartskan.data.ThemeMode
import com.example.smartskan.util.filesLabel
import kotlinx.coroutines.launch

/** The Settings tab: appearance, ads/purchases, scanner behaviour, storage, support and app info. */
@Composable
fun SettingsScreen(repo: Repository, onOpenPrivacyPolicy: () -> Unit, onOpenTerms: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = LocalAppSettings.current
    val themeMode by settings.themeMode.collectAsState()
    val colourStyle by settings.colourStyle.collectAsState()
    val scanMode by settings.scanMode.collectAsState()
    val galleryImport by settings.galleryImport.collectAsState()
    val docs by repo.documents.items.collectAsState()
    val signature by repo.signature.collectAsState()
    val activity = LocalActivity.current
    val purchases = LocalPurchases.current
    val ads = LocalAds.current
    val adFree by purchases.adFree.collectAsState()
    val price by purchases.price.collectAsState()
    val restoring by purchases.busy.collectAsState()
    val privacyOptionsRequired by ads.privacyOptionsRequired.collectAsState()
    LaunchedEffect(purchases) { purchases.messages.collect { toast(context, it.resolve(context)) } }

    var choosingTheme by remember { mutableStateOf(false) }
    var choosingColourStyle by remember { mutableStateOf(false) }
    var choosingLanguage by remember { mutableStateOf(false) }
    val language = remember { AppLanguages.current() }
    var choosingScanMode by remember { mutableStateOf(false) }
    var confirmingRemoveSignature by remember { mutableStateOf(false) }
    // Bumped to re-measure storage after clearing the cache or when documents change.
    var storageVersion by remember { mutableIntStateOf(0) }
    var docBytes by remember { mutableStateOf<Long?>(null) }
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(storageVersion, docs) {
        docBytes = repo.documentStorageBytes()
        cacheBytes = repo.cacheBytes()
    }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"
    }

    ScreenScaffold(title = stringResource(R.string.tab_settings)) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 48.dp,
            ),
        ) {
            item { SectionHeader(stringResource(R.string.section_appearance)) }
            item {
                SettingRow(Icons.Outlined.DarkMode, stringResource(R.string.setting_theme), stringResource(themeMode.label)) { choosingTheme = true }
            }
            item {
                SettingRow(
                    Icons.Outlined.ColorLens, stringResource(R.string.setting_colour_style),
                    "${stringResource(colourStyle.label)} · ${stringResource(colourStyle.description)}",
                ) {
                    choosingColourStyle = true
                }
            }
            item {
                SettingRow(Icons.Outlined.Language, stringResource(R.string.setting_language), languageName(language)) {
                    choosingLanguage = true
                }
            }

            item { SectionDivider(); SectionHeader(stringResource(R.string.remove_ads)) }
            item {
                if (adFree) {
                    SettingRow(Icons.Outlined.Verified, stringResource(R.string.ads_removed), stringResource(R.string.ads_removed_thanks))
                } else {
                    SettingRow(Icons.Outlined.Block, stringResource(R.string.remove_ads), stringResource(R.string.remove_ads_price, price)) {
                        if (activity != null) purchases.buyRemoveAds(activity)
                    }
                }
            }
            item {
                SettingRow(
                    Icons.Outlined.Restore, stringResource(R.string.restore_purchases),
                    stringResource(if (restoring) R.string.restore_checking else R.string.restore_hint),
                    enabled = !restoring,
                ) {
                    scope.launch {
                        toast(
                            context,
                            context.getString(
                                when (purchases.restore()) {
                                    Purchases.RestoreResult.RESTORED -> R.string.restore_ok
                                    Purchases.RestoreResult.NOTHING_FOUND -> R.string.restore_none
                                    Purchases.RestoreResult.FAILED -> R.string.restore_failed
                                },
                            ),
                        )
                    }
                }
            }
            if (!adFree && privacyOptionsRequired) {
                item {
                    SettingRow(Icons.Outlined.PrivacyTip, stringResource(R.string.ad_privacy_title), stringResource(R.string.ad_privacy_sub)) {
                        if (activity != null) ads.showPrivacyOptions(activity)
                    }
                }
            }

            item { SectionDivider(); SectionHeader(stringResource(R.string.section_scanning)) }
            item {
                SettingRow(
                    Icons.Outlined.DocumentScanner, stringResource(R.string.setting_scanner_mode),
                    "${stringResource(scanMode.label)} · ${stringResource(scanMode.description)}",
                ) {
                    choosingScanMode = true
                }
            }
            item {
                SwitchRow(
                    Icons.Outlined.PhotoLibrary, stringResource(R.string.gallery_title), stringResource(R.string.gallery_sub),
                    galleryImport, settings::setGalleryImport,
                )
            }

            item { SectionDivider(); SectionHeader(stringResource(R.string.section_storage)) }
            item {
                val size = docBytes?.let { Formatter.formatShortFileSize(context, it) } ?: "…"
                SettingRow(Icons.Outlined.Folder, stringResource(R.string.tab_documents), "${filesLabel(context, docs.size)} · $size")
            }
            item {
                val size = cacheBytes?.let { Formatter.formatShortFileSize(context, it) } ?: "…"
                SettingRow(Icons.Outlined.CleaningServices, stringResource(R.string.clear_temp_title), stringResource(R.string.clear_temp_sub, size)) {
                    scope.launch {
                        repo.clearCache()
                        storageVersion++
                        toast(context, context.getString(R.string.clear_temp_done))
                    }
                }
            }
            item {
                SettingRow(
                    Icons.Outlined.Draw, stringResource(R.string.remove_signature_title),
                    stringResource(if (signature == null) R.string.remove_signature_none else R.string.remove_signature_used),
                    enabled = signature != null,
                ) { confirmingRemoveSignature = true }
            }

            item { SectionDivider(); SectionHeader(stringResource(R.string.section_support)) }
            item {
                SettingRow(Icons.Outlined.Email, stringResource(R.string.contact_us), CONTACT_EMAIL) {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(CONTACT_EMAIL))
                        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.feedback_subject, version))
                    }
                    startSafely(context, intent, context.getString(R.string.no_email_app, CONTACT_EMAIL))
                }
            }
            item {
                SettingRow(Icons.Outlined.Policy, stringResource(R.string.privacy_title), stringResource(R.string.privacy_sub), onClick = onOpenPrivacyPolicy)
            }
            item {
                SettingRow(Icons.Outlined.Description, stringResource(R.string.terms_title), stringResource(R.string.terms_sub), onClick = onOpenTerms)
            }

            item { SectionDivider(); SectionHeader(stringResource(R.string.section_about)) }
            item { SettingRow(Icons.Outlined.Info, "SmartSkan", stringResource(R.string.about_version, version, COMPANY_NAME)) }
        }
    }

    if (choosingTheme) {
        ChoiceDialog(
            title = stringResource(R.string.setting_theme),
            options = ThemeMode.entries,
            selected = themeMode,
            label = { stringResource(it.label) },
            onSelect = { settings.setThemeMode(it); choosingTheme = false },
            onDismiss = { choosingTheme = false },
        )
    }
    if (choosingColourStyle) {
        ChoiceDialog(
            title = stringResource(R.string.setting_colour_style),
            // Wallpaper colours need Android 12+.
            options = ColourStyle.entries.filter { it != ColourStyle.WALLPAPER || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S },
            selected = colourStyle,
            label = { stringResource(it.label) },
            description = { stringResource(it.description) },
            onSelect = { settings.setColourStyle(it); choosingColourStyle = false },
            onDismiss = { choosingColourStyle = false },
        )
    }
    if (choosingScanMode) {
        ChoiceDialog(
            title = stringResource(R.string.setting_scanner_mode),
            options = ScanMode.entries,
            selected = scanMode,
            label = { stringResource(it.label) },
            description = { stringResource(it.description) },
            onSelect = { settings.setScanMode(it); choosingScanMode = false },
            onDismiss = { choosingScanMode = false },
        )
    }
    if (choosingLanguage) {
        ChoiceDialog(
            title = stringResource(R.string.setting_language),
            options = AppLanguages.all,
            selected = language,
            label = { languageName(it) },
            onSelect = { choosingLanguage = false; AppLanguages.apply(it) },
            onDismiss = { choosingLanguage = false },
        )
    }
    if (confirmingRemoveSignature) {
        ConfirmDialog(
            stringResource(R.string.remove_signature_confirm_title), stringResource(R.string.remove_signature_confirm_body), stringResource(R.string.action_remove),
            onConfirm = { repo.clearSignature() }, onDismiss = { confirmingRemoveSignature = false },
        )
    }
}

@Composable
private fun languageName(language: AppLanguage) =
    if (language === AppLanguages.SYSTEM) stringResource(R.string.theme_system) else language.name

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SectionDivider() = HorizontalDivider(Modifier.padding(top = 8.dp))

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.38f
    ListItem(
        headlineContent = { Text(title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)) },
        supportingContent = { Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)) },
        modifier = if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier,
    )
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.selectable(selected = checked, role = Role.Switch) { onChange(!checked) },
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    description: (@Composable (T) -> String)? = null,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(label(option), style = MaterialTheme.typography.bodyLarge)
                            description?.let {
                                Text(it(option), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
