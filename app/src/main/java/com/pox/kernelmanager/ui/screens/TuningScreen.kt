package com.pox.kernelmanager.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pox.kernelmanager.data.model.EasyModePresets
import com.pox.kernelmanager.data.model.EasyModeProfile
import com.pox.kernelmanager.data.model.EasyModeTier
import com.pox.kernelmanager.data.repo.TuningRepository
import com.pox.kernelmanager.ui.components.PoxTopAppBar
import com.pox.kernelmanager.ui.components.SectionHeader
import com.pox.kernelmanager.ui.viewmodel.FeaturesViewModel
import kotlinx.coroutines.launch

enum class TuningModeTab(val title: String) {
    EASY("Easy Mode"),
    ADVANCED("Advanced Mode")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuningScreen(
    viewModel: FeaturesViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(TuningModeTab.EASY) }
    val scope = rememberCoroutineScope()
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
                title = "Kernel Tuning"
            )
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Mode Selector: Easy vs Advanced Segmented Switch
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TuningModeTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        Surface(
                            onClick = { selectedTab = tab },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (tab == TuningModeTab.EASY) Icons.Default.Tune else Icons.Default.Build,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = tab.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Crossfade(
                targetState = selectedTab,
                label = "TuningModeCrossfade",
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                when (tab) {
                    TuningModeTab.EASY -> EasyModeView(
                        onApplyPreset = { preset ->
                            scope.launch {
                                val result = TuningRepository.applyEasyPreset(preset)
                                snackbarHostState.showSnackbar(result.message)
                                viewModel.refresh()
                            }
                        }
                    )
                    TuningModeTab.ADVANCED -> FeaturesScreenContent(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EasyModeView(
    onApplyPreset: (com.pox.kernelmanager.data.model.EasyPresetConfig) -> Unit
) {
    var selectedTier by remember { mutableStateOf(TuningRepository.getActiveEasyTier()) }
    var selectedProfile by remember { mutableStateOf(TuningRepository.getActiveEasyProfile()) }
    val scrollState = rememberScrollState()

    val currentConfig = remember(selectedTier, selectedProfile) {
        EasyModePresets.getPreset(selectedTier, selectedProfile)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp)
    ) {
        // Section: Tier Selector (5 Tiers)
        SectionHeader(
            title = "Performance Tiers",
            subtitle = "Select performance envelope from ultra-efficient to uncapped peak"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EasyModeTier.values().forEach { tier ->
                val isSelected = selectedTier == tier
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedTier = tier },
                    label = { Text(tier.title) },
                    leadingIcon = {
                        Icon(
                            imageVector = tier.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Section: Profile Selector (3 Sub-profiles)
        SectionHeader(
            title = "Optimization Profile",
            subtitle = "Fine-tune governor bias, background tasks and responsiveness"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EasyModeProfile.values().forEach { profile ->
                val isSelected = selectedProfile == profile
                Surface(
                    onClick = { selectedProfile = profile },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = profile.icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = profile.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Preset Details Hero Card
        com.pox.kernelmanager.ui.theme.PoxCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = selectedTier.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "${selectedTier.title} • ${selectedProfile.title}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = selectedTier.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Summary Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    currentConfig.summaryHighlights.forEach { highlight ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = highlight,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Detailed Specs
                Text(
                    text = "Configured Kernel Features:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                ConfigParamRow("Governor / Gaming Mode", when (currentConfig.gamingMode) {
                    -1 -> "Powersave Governor (-1)"
                    1 -> "Gaming Mode Boost (1)"
                    2 -> "Extreme Boost Max OPP (2)"
                    else -> "Balanced Governor (0)"
                })
                ConfigParamRow("Kprofiles Tier", when (currentConfig.kprofileLevel) {
                    1 -> "Battery Priority"
                    2 -> "Balanced Daily"
                    3 -> "Performance Peak"
                    else -> "Balanced"
                })
                ConfigParamRow("BORE Scheduler", "Active (Burst Scale: ${currentConfig.burstPenaltyScale}, Offset: ${currentConfig.burstPenaltyOffset})")
                ConfigParamRow("Touch Sampling Rate", if (currentConfig.touchGameMode) "240Hz High Polling Active" else "Standard 120Hz")
                ConfigParamRow("Hardware Charge Wattage", "${currentConfig.chargeWatt}W Locked (Dual Switch)")
                ConfigParamRow("Silicon Health Calibration", when (currentConfig.healthCalibration) {
                    2 -> "Performance Guard (OPP Shield)"
                    3 -> "Maximum Longevity (Voltage Safe)"
                    else -> "Auto-Adaptive (Zero Brownouts)"
                })

                Spacer(modifier = Modifier.height(4.dp))

                val tokens = com.pox.kernelmanager.ui.theme.LocalThemeTokens.current
                Button(
                    onClick = { onApplyPreset(currentConfig) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = tokens.pillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.themeAccent
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Apply ${selectedTier.title} (${selectedProfile.title}) Preset",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Self-Learning Banner
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Adaptive Silicon Calibration Armed",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "The background engine learns your battery degradation and silicon voltage margins to optimize preset efficiency.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigParamRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
