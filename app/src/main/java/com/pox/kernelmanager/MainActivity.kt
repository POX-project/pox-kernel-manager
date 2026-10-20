package com.pox.kernelmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pox.kernelmanager.ui.screens.DashboardScreen
import com.pox.kernelmanager.ui.screens.DiagnosticsScreen
import com.pox.kernelmanager.ui.screens.SettingsScreen
import com.pox.kernelmanager.ui.screens.TuningScreen
import com.pox.kernelmanager.ui.screens.UpdaterScreen
import com.pox.kernelmanager.ui.theme.PoxTheme
import com.pox.kernelmanager.ui.viewmodel.DashboardViewModel
import com.pox.kernelmanager.ui.viewmodel.DiagnosticsViewModel
import com.pox.kernelmanager.ui.viewmodel.FeaturesViewModel
import com.pox.kernelmanager.ui.viewmodel.UpdaterViewModel

data class NavItem(
    val title: String,
    val icon: ImageVector
)

enum class SettingsSubScreen {
    MAIN,
    UPDATER,
    DIAGNOSTICS
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PoxTheme {
                MainContent()
            }
        }
    }
}

@Composable
fun MainContent() {
    val navItems = listOf(
        NavItem(title = "Homepage", icon = Icons.Default.Speed),
        NavItem(title = "Tuning", icon = Icons.Default.Tune),
        NavItem(title = "Settings", icon = Icons.Default.Settings)
    )

    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var settingsSubScreen by rememberSaveable { mutableStateOf(SettingsSubScreen.MAIN) }

    val dashboardViewModel: DashboardViewModel = viewModel()
    val featuresViewModel: FeaturesViewModel = viewModel()
    val updaterViewModel: UpdaterViewModel = viewModel()
    val diagnosticsViewModel: DiagnosticsViewModel = viewModel()

    BackHandler(enabled = selectedIndex == 2 && settingsSubScreen != SettingsSubScreen.MAIN) {
        settingsSubScreen = SettingsSubScreen.MAIN
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = {
                            selectedIndex = index
                            if (index != 2) {
                                settingsSubScreen = SettingsSubScreen.MAIN
                            }
                        },
                        icon = { Icon(imageVector = item.icon, contentDescription = item.title) },
                        label = { Text(item.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = selectedIndex,
            label = "ScreenCrossfade",
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) { index ->
            when (index) {
                0 -> DashboardScreen(viewModel = dashboardViewModel)
                1 -> TuningScreen(viewModel = featuresViewModel)
                2 -> {
                    Crossfade(
                        targetState = settingsSubScreen,
                        label = "SettingsSubScreenCrossfade"
                    ) { subScreen ->
                        when (subScreen) {
                            SettingsSubScreen.MAIN -> SettingsScreen(
                                onNavigateToUpdater = { settingsSubScreen = SettingsSubScreen.UPDATER },
                                onNavigateToDiagnostics = { settingsSubScreen = SettingsSubScreen.DIAGNOSTICS }
                            )
                            SettingsSubScreen.UPDATER -> UpdaterScreen(
                                viewModel = updaterViewModel,
                                onBack = { settingsSubScreen = SettingsSubScreen.MAIN }
                            )
                            SettingsSubScreen.DIAGNOSTICS -> DiagnosticsScreen(
                                viewModel = diagnosticsViewModel,
                                onBack = { settingsSubScreen = SettingsSubScreen.MAIN }
                            )
                        }
                    }
                }
            }
        }
    }
}
