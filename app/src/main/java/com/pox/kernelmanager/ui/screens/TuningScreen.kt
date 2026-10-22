package com.pox.kernelmanager.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pox.kernelmanager.data.model.EasyModePresets
import com.pox.kernelmanager.data.model.EasyModeProfile
import com.pox.kernelmanager.data.model.EasyModeTier
import com.pox.kernelmanager.data.repo.TuningRepository
import com.pox.kernelmanager.ui.components.PoxTopAppBar
import com.pox.kernelmanager.ui.components.SectionHeader
import com.pox.kernelmanager.ui.theme.PoxCard
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
            // Segmented Button Row: Easy vs Advanced
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TuningModeTab.entries.forEachIndexed { index, tab ->
                        SegmentedButton(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = TuningModeTab.entries.size),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = selectedTab == tab) {
                                    Icon(
                                        imageVector = if (tab == TuningModeTab.EASY) Icons.Default.Tune else Icons.Default.Build,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        ) {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EasyModeView(
    onApplyPreset: (com.pox.kernelmanager.data.model.EasyPresetConfig) -> Unit
) {
    var selectedTier by remember { mutableStateOf(TuningRepository.getActiveEasyTier()) }
    var selectedProfile by remember { mutableStateOf(TuningRepository.getActiveEasyProfile()) }
    val scrollState = rememberScrollState()
    val tierScrollState = rememberScrollState()

    val currentConfig = remember(selectedTier, selectedProfile) {
        EasyModePresets.getPreset(selectedTier, selectedProfile)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp)
    ) {
        // Section: Tier Selector (Scrollable horizontally to prevent squishing)
        SectionHeader(
            title = "Performance Tiers",
            subtitle = "Swipe to select power envelope from Battery Saver to Gaming"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(tierScrollState)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EasyModeTier.entries.forEach { tier ->
                val isSelected = selectedTier == tier
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedTier = tier },
                    label = {
                        Text(
                            text = tier.title,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = tier.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
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
            EasyModeProfile.entries.forEach { profile ->
                val isSelected = selectedProfile == profile
                Surface(
                    onClick = { selectedProfile = profile },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = profile.icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = profile.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Preset Details Hero Card
        PoxCard(
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = selectedTier.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${selectedTier.title} • ${selectedProfile.title}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = selectedTier.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Summary Chips wrapped with FlowRow to prevent horizontal clipping
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    currentConfig.summaryHighlights.forEach { highlight ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = highlight,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
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
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                ConfigParamRow("Governor Mode", when (currentConfig.gamingMode) {
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
                ConfigParamRow("BORE Scheduler", "Scale: ${currentConfig.burstPenaltyScale}, Offset: ${currentConfig.burstPenaltyOffset}")
                ConfigParamRow("Touch Sampling", if (currentConfig.touchGameMode) "240Hz High Polling" else "Standard 120Hz")
                ConfigParamRow("Charge Wattage", "${currentConfig.chargeWatt}W Locked")
                ConfigParamRow("Health Calibration", when (currentConfig.healthCalibration) {
                    2 -> "Performance Guard"
                    3 -> "Longevity Safe"
                    else -> "Auto-Adaptive"
                })

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = { onApplyPreset(currentConfig) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Apply ${selectedTier.title} Preset",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Adaptive Silicon Calibration Armed",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "The background engine learns your battery degradation and silicon voltage margins to optimize preset efficiency.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigParamRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.2f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
