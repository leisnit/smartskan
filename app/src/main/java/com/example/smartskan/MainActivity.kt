package com.example.smartskan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.smartskan.data.Repository
import com.example.smartskan.ui.CodesScreen
import com.example.smartskan.ui.DocumentsScreen
import com.example.smartskan.ui.SpendingScreen
import com.example.smartskan.ui.TextScreen
import com.example.smartskan.ui.theme.SmartSkanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as SmartSkanApplication).repository
        setContent {
            SmartSkanTheme {
                MainScreen(repository)
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    DOCUMENTS("Documents", Icons.Outlined.Description),
    CODES("Codes", Icons.Outlined.QrCodeScanner),
    TEXT("Text", Icons.Outlined.TextFields),
    SPENDING("Spending", Icons.Outlined.Receipt),
}

@Composable
private fun MainScreen(repository: Repository) {
    var tab by rememberSaveable { mutableStateOf(Tab.DOCUMENTS) }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when (tab) {
                Tab.DOCUMENTS -> DocumentsScreen(repository)
                Tab.CODES -> CodesScreen(repository)
                Tab.TEXT -> TextScreen()
                Tab.SPENDING -> SpendingScreen(repository)
            }
        }
    }
}
