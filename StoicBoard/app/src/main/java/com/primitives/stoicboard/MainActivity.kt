package com.primitives.stoicboard

import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

enum class AppTab { HOME, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val settings by vm.settings.collectAsState()

            val baseContext = LocalContext.current

            val localizedContext = remember(settings.appLanguage) {
                val sysLocale = Resources.getSystem().configuration.locales[0]
                val locale = if (settings.appLanguage == "system") sysLocale else Locale(settings.appLanguage)
                Locale.setDefault(locale)
                val config = Configuration(baseContext.resources.configuration)
                config.setLocale(locale)
                baseContext.createConfigurationContext(config)
            }

            val localizedConfig = remember(settings.appLanguage, localizedContext) {
                localizedContext.resources.configuration
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfig
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = BgDark
                ) { padding ->
                    AppRoot(padding = padding)
                }
            }
        }
    }
}

@Composable
fun AppRoot(padding: PaddingValues) {
    var tab by remember { mutableStateOf(AppTab.HOME) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(padding)
    ) {
        when (tab) {
            AppTab.HOME -> DashboardScreen(onSettings = { tab = AppTab.SETTINGS })
            AppTab.SETTINGS -> SettingsScreen(onBack = { tab = AppTab.HOME })
        }
    }
}