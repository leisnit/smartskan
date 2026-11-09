package com.example.smartskan

import androidx.compose.ui.res.stringResource
import com.example.smartskan.R
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.smartskan.ads.BannerAd
import com.example.smartskan.ads.LocalAds
import com.example.smartskan.data.LocalAppSettings
import com.example.smartskan.data.LocalPurchases
import com.example.smartskan.data.Repository
import com.example.smartskan.ui.AppTool
import com.example.smartskan.ui.DocumentsScreen
import com.example.smartskan.ui.HomeScreen
import com.example.smartskan.ui.PrivacyPolicyScreen
import com.example.smartskan.ui.SettingsScreen
import com.example.smartskan.ui.TermsOfUseScreen
import com.example.smartskan.ui.ToolHost
import com.example.smartskan.ui.ToolsScreen
import com.example.smartskan.ui.rememberSmartScan
import com.example.smartskan.ui.theme.SmartSkanTheme
import com.example.smartskan.ui.theme.isDark
import java.util.Locale

// Same scrims enableEdgeToEdge() uses by default for 3-button navigation.
private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before Android 13 a language picked in the app doesn't reach dates and numbers on its own.
        Locale.setDefault(resources.configuration.locales[0])
        enableEdgeToEdge()
        val app = application as SmartSkanApplication
        val repository = app.repository
        val settings = app.settings
        val purchases = app.purchases
        val ads = app.ads
        purchases.refresh()
        // Paying users never see the consent form or ads.
        if (!purchases.adFree.value) ads.gatherConsent(this)
        setContent {
            val themeMode by settings.themeMode.collectAsState()
            val colourStyle by settings.colourStyle.collectAsState()
            val dark = themeMode.isDark()
            // Keep status/navigation bar icons readable when the app theme differs from the system's.
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
            }
            CompositionLocalProvider(
                LocalAppSettings provides settings,
                LocalPurchases provides purchases,
                LocalAds provides ads,
            ) {
                SmartSkanTheme(themeMode = themeMode, colourStyle = colourStyle) {
                    MainScreen(repository)
                }
            }
        }
    }
}

private enum class Tab(@StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME(R.string.tab_home, Icons.Outlined.Home, Icons.Filled.Home),
    DOCUMENTS(R.string.tab_documents, Icons.Outlined.Folder, Icons.Filled.Folder),
    TOOLS(R.string.tab_tools, Icons.Outlined.GridView, Icons.Filled.GridView),
    SETTINGS(R.string.tab_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}

@Composable
private fun MainScreen(repository: Repository) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    // A tool opens full screen over the tabs; back returns to the tab it was opened from.
    var openTool by rememberSaveable { mutableStateOf<AppTool?>(null) }
    var scanQrOnOpen by rememberSaveable { mutableStateOf(false) }
    var showPrivacyPolicy by rememberSaveable { mutableStateOf(false) }
    var showTerms by rememberSaveable { mutableStateOf(false) }
    val adFree by LocalPurchases.current.adFree.collectAsState()
    val adsReady by LocalAds.current.ready.collectAsState()
    val showAds = adsReady && !adFree
    val smartScan = rememberSmartScan(repository)

    BackHandler(enabled = openTool != null) { openTool = null }
    BackHandler(enabled = showPrivacyPolicy) { showPrivacyPolicy = false }
    BackHandler(enabled = showTerms) { showTerms = false }
    BackHandler(enabled = openTool == null && tab != Tab.HOME) { tab = Tab.HOME }

    val tool = openTool
    if (tool != null) {
        ToolHost(
            tool = tool,
            repo = repository,
            scanQrOnOpen = scanQrOnOpen,
            onScanQrOpened = { scanQrOnOpen = false },
            onBack = { openTool = null },
        )
        return
    }
    if (showPrivacyPolicy) {
        PrivacyPolicyScreen(onBack = { showPrivacyPolicy = false })
        return
    }
    if (showTerms) {
        TermsOfUseScreen(onBack = { showTerms = false })
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                TabItem(Tab.HOME, tab) { tab = it }
                TabItem(Tab.DOCUMENTS, tab) { tab = it }
                // Empty slot under the centre + button.
                NavigationBarItem(selected = false, onClick = {}, enabled = false, icon = {})
                TabItem(Tab.TOOLS, tab) { tab = it }
                TabItem(Tab.SETTINGS, tab) { tab = it }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            // Pushed down so it sits on the bottom bar, centred over the empty slot.
            FloatingActionButton(
                onClick = smartScan.launch,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                modifier = Modifier.offset(y = 48.dp).size(64.dp),
            ) {
                Icon(Icons.Filled.Add, stringResource(R.string.new_scan), Modifier.size(32.dp))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            // Banner sits at the top of each tab: at the bottom the centre + button would overlap it.
            if (showAds) BannerAd(Modifier.windowInsetsPadding(WindowInsets.statusBars))
            Box(
                Modifier
                    .weight(1f)
                    .then(if (showAds) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
            ) {
                when (tab) {
                    Tab.HOME -> HomeScreen(
                        repo = repository,
                        scanSaving = smartScan.saving,
                        onOpenTool = { openTool = it },
                        onScanQr = { scanQrOnOpen = true; openTool = AppTool.QR_CODES },
                        onAllDocuments = { tab = Tab.DOCUMENTS },
                    )
                    Tab.DOCUMENTS -> DocumentsScreen(repository)
                    Tab.TOOLS -> ToolsScreen(onOpen = { openTool = it })
                    Tab.SETTINGS -> SettingsScreen(
                        repository,
                        onOpenPrivacyPolicy = { showPrivacyPolicy = true },
                        onOpenTerms = { showTerms = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(tab: Tab, current: Tab, onSelect: (Tab) -> Unit) {
    val selected = tab == current
    NavigationBarItem(
        selected = selected,
        onClick = { onSelect(tab) },
        icon = { Icon(if (selected) tab.selectedIcon else tab.icon, null) },
        label = { Text(stringResource(tab.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}
