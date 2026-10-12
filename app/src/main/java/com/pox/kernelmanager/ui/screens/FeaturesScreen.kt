package com.pox.kernelmanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pox.kernelmanager.data.model.GamingModeLevel
import com.pox.kernelmanager.ui.components.*
import com.pox.kernelmanager.ui.viewmodel.FeaturesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturesScreen(
    viewModel: FeaturesViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        topBar = {
            PoxTopAppBar(
                title = "Kernel Tuning",
                onRefresh = { viewModel.loadFeatures() },
                isRefreshing = uiState.isLoading
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            // 1. Gaming Mode & KProfiles
            SectionHeader(
                title = "Gaming & Performance Governor",
                subtitle = "Unified /proc/perfmgr/gaming_mode controller"
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 5.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Active Performance Profile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        GamingModeLevel.entries.forEachIndexed { index, level ->
                            SegmentedButton(
                                selected = uiState.features.gamingMode == level,
                                onClick = { viewModel.setGamingMode(level) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = GamingModeLevel.entries.size)
                            ) {
                                Text(
                                    text = when (level) {
                                        GamingModeLevel.BALANCED -> "Balanced"
                                        GamingModeLevel.GAMING -> "Gaming"
                                        GamingModeLevel.EXTREME -> "Extreme"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = uiState.features.gamingMode.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FeatureSwitchCard(
                title = "240Hz Touch Gaming & Debounce",
                description = "Zero-debounce sampling & instant Mali touch frequency ramp",
                isChecked = uiState.features.touchBoost240Hz,
                onCheckedChange = { /* managed via gaming mode */ },
                icon = Icons.Default.TouchApp,
                enabled = false
            )

            // 2. Battery & Direct Bypass
            SectionHeader(
                title = "Smart Battery & Bypass Charging",
                subtitle = "Direct power to motherboard without battery stress"
            )

            FeatureSwitchCard(
                title = "Direct-Power Bypass Mode",
                description = "Power phone directly via charger port, halting battery heating and degradation during gaming or heavy workloads",
                isChecked = uiState.features.bypassCharging,
                onCheckedChange = { viewModel.toggleBypassCharging(it) },
                icon = Icons.Default.ElectricBolt
            )

            FeatureSliderCard(
                title = "Charging Stop Limit",
                valueLabel = "${uiState.features.chargeLimitPercent}%",
                description = "Stops charging when battery reaches this threshold to prolong lifespan",
                value = uiState.features.chargeLimitPercent.toFloat(),
                valueRange = 60f..100f,
                steps = 7,
                onValueChange = { viewModel.setChargeLimit(it.toInt()) }
            )

            FeatureSwitchCard(
                title = "Hardware Thermal Guard",
                description = "Clamps charging current when battery temperature exceeds ${uiState.features.tempLimitC}°C",
                isChecked = uiState.features.thermalGuardEnabled,
                onCheckedChange = { viewModel.toggleThermalGuard(it, uiState.features.tempLimitC) },
                icon = Icons.Default.Thermostat
            )

            // 3. MIUI / HyperOS xswapd Subsystem
            SectionHeader(
                title = "HyperOS xswapd Daemon",
                subtitle = "Asynchronous per-memcg page swap & memory compressor"
            )

            FeatureSwitchCard(
                title = "xswapd Background Engine",
                description = "Offloads memory swap compression to unbound kernel workers, preventing binder stalls and launcher redraws",
                isChecked = uiState.features.xswapdEnabled,
                onCheckedChange = { viewModel.toggleXswapd(it) },
                icon = Icons.Default.Compress
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 5.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Force Memory Reclaim",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Triggers an immediate 100MB xswapd reclaim pass across all apps",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    FilledTonalButton(
                        onClick = { viewModel.triggerXswapdReclaim() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Reclaim")
                    }
                }
            }

            // 4. Display, Touch & Vibrator
            SectionHeader(
                title = "Display, Touch & Haptics",
                subtitle = "Hardware double-tap to wake, True Tone & motor gain"
            )

            FeatureSwitchCard(
                title = "Double Tap to Wake (DT2W)",
                description = "Wake screen by double tapping touch panel in deep sleep",
                isChecked = uiState.features.doubleTapToWake,
                onCheckedChange = { viewModel.toggleDt2w(it) },
                icon = Icons.Default.TouchApp
            )

            FeatureSwitchCard(
                title = "True Tone Liquid Retina D65",
                description = "Calibrated D65 color balance reference profile for begonia LCD",
                isChecked = uiState.features.trueToneDisplay,
                onCheckedChange = { viewModel.toggleTrueTone(it) },
                icon = Icons.Default.Palette
            )

            FeatureSliderCard(
                title = "Hardware Vibrator Strength",
                valueLabel = "${uiState.features.vibratorStrengthPercent}%",
                description = "Unlocked 3.3V analog haptic motor voltage driving range",
                value = uiState.features.vibratorStrengthPercent.toFloat(),
                valueRange = 0f..100f,
                steps = 19,
                onValueChange = { viewModel.setVibratorStrength(it.toInt()) }
            )

            // 5. Storage & Filesystem
            SectionHeader(
                title = "Storage & Dynamic Fsync",
                subtitle = "Micro-stutter elimination & UFS flash scheduler"
            )

            FeatureSwitchCard(
                title = "Dynamic Fsync",
                description = "Disables synchronous flush when screen is on, flushing immediately when screen goes off",
                isChecked = uiState.features.dynamicFsync,
                onCheckedChange = { viewModel.toggleDynamicFsync(it) },
                icon = Icons.Default.Storage
            )

            FeatureSliderCard(
                title = "UFS Queue Readahead",
                valueLabel = "${uiState.features.readAheadKb} KB",
                description = "Optimized readahead buffer size for sequential flash throughput",
                value = uiState.features.readAheadKb.toFloat(),
                valueRange = 64f..512f,
                steps = 6,
                onValueChange = { viewModel.setReadAhead(it.toInt()) }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
