package com.pox.kernelmanager.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
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
import com.pox.kernelmanager.data.learning.AdaptiveLearningManager
import com.pox.kernelmanager.data.model.BrainlessPresets
import com.pox.kernelmanager.data.model.BrainlessProfile
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
    BRAINLESS("Brainless"),
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
    val initialTab = remember {
        when (TuningRepository.getActiveTuningMode()) {
            "EASY" -> TuningModeTab.EASY
            "ADVANCED" -> TuningModeTab.ADVANCED
            else -> TuningModeTab.BRAINLESS
        }
    }
    var selectedTab by remember { mutableStateOf(initialTab) }
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
            // Segmented Button Row: Brainless vs Easy vs Advanced
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
                            onClick = {
                                selectedTab = tab
                                TuningRepository.setActiveTuningMode(tab.name)
                                if (tab == TuningModeTab.ADVANCED) {
                                    viewModel.refresh()
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = TuningModeTab.entries.size),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = selectedTab == tab) {
                                    Icon(
                                        imageVector = when (tab) {
                                            TuningModeTab.BRAINLESS -> Icons.Default.Bolt
                                            TuningModeTab.EASY -> Icons.Default.Tune
                                            TuningModeTab.ADVANCED -> Icons.Default.Build
                                        },
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
                    TuningModeTab.BRAINLESS -> BrainlessModeView(
                        onApplyProfile = { profile ->
                            viewModel.applyBrainlessProfile(profile)
                        }
                    )
                    TuningModeTab.EASY -> EasyModeView(
                        onApplyPreset = { preset ->
                            viewModel.applyEasyPreset(preset)
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

    val learningStats = remember { AdaptiveLearningManager.getStats() }
    val currentConfig = remember(selectedTier, selectedProfile) {
        EasyModePresets.getPreset(selectedTier, selectedProfile)
    }
    val adaptedConfig = remember(selectedTier, selectedProfile, learningStats) {
        EasyModePresets.adaptPreset(currentConfig, learningStats)
    }
    val isPresetActive = TuningRepository.getActiveTuningMode() == "EASY" &&
        selectedTier == TuningRepository.getActiveEasyTier() &&
        selectedProfile == TuningRepository.getActiveEasyProfile()

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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${selectedTier.title} • ${selectedProfile.title}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isPresetActive) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = "ACTIVE & BOUND",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (adaptedConfig.isAdapted) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = "Adapted",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
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

                ConfigParamRow("Governor Mode", when (adaptedConfig.effectiveGamingMode) {
                    -1 -> "Powersave Governor (-1)"
                    1 -> "Gaming Mode Boost (1)"
                    2 -> "Extreme Boost Max OPP (2)"
                    else -> "Balanced Governor (0)"
                })
                ConfigParamRow("Kprofiles Tier", when (adaptedConfig.effectiveKprofileLevel) {
                    1 -> "Battery Priority"
                    2 -> "Balanced Daily"
                    3 -> "Performance Peak"
                    else -> "Balanced"
                })
                ConfigParamRow(
                    "Big Cluster Peak",
                    if (adaptedConfig.effectiveOppCeilingKhz < currentConfig.baseOppCeilingKhz)
                        "${adaptedConfig.effectiveOppCeilingKhz / 1000} MHz (Guarded)"
                    else
                        "${adaptedConfig.effectiveOppCeilingKhz / 1000} MHz Max"
                )
                ConfigParamRow(
                    "BORE Scheduler",
                    "Scale: ${adaptedConfig.effectiveBurstPenaltyScale}, Offset: ${adaptedConfig.effectiveBurstPenaltyOffset}"
                )
                ConfigParamRow("Touch Sampling", if (adaptedConfig.effectiveTouchGameMode) "240Hz High Polling" else "Standard 120Hz")
                ConfigParamRow(
                    "Charge Wattage",
                    if (adaptedConfig.effectiveChargeWatt < currentConfig.chargeWatt)
                        "${adaptedConfig.effectiveChargeWatt}W (Thermal Scaled)"
                    else
                        "${adaptedConfig.effectiveChargeWatt}W Locked"
                )
                ConfigParamRow("Health Calibration", when (adaptedConfig.effectiveHealthCalibration) {
                    2 -> "Performance Guard"
                    3 -> "Longevity Mode"
                    else -> "Auto-Adaptive"
                })

                if (adaptedConfig.adaptationNotes.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Silicon & Usage Adaptations Active",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        adaptedConfig.adaptationNotes.forEach { note ->
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = { onApplyPreset(currentConfig) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = if (isPresetActive) Icons.Default.CheckCircle else Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPresetActive) {
                            if (adaptedConfig.isAdapted) "Re-Bind & Apply Adapted ${selectedTier.title} Preset" else "Re-Bind & Apply ${selectedTier.title} Preset"
                        } else {
                            if (adaptedConfig.isAdapted) "Apply & Bind Adapted ${selectedTier.title} Preset" else "Apply & Bind ${selectedTier.title} Preset"
                        },
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
                        text = "Continuous Silicon & Usage Learning Active",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Presets adjust their operational OPP ceilings, charging wattages, and scheduler parameters continuously as your battery health, internal resistance (DCIR), and phone usage change over time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrainlessModeView(
    onApplyProfile: (BrainlessProfile) -> Unit
) {
    var activeProfile by remember { mutableStateOf(TuningRepository.getActiveBrainlessProfile()) }
    val learningStats = remember { AdaptiveLearningManager.getStats() }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp)
    ) {
        SectionHeader(
            title = "Brainless Profiles",
            subtitle = "Zero effort. One-tap tuning continuously personalized to your silicon."
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BrainlessProfile.entries.forEach { profile ->
                val isActive = TuningRepository.getActiveTuningMode() == "BRAINLESS" && activeProfile == profile
                val baseConfig = remember(profile) { BrainlessPresets.getPreset(profile) }
                val adaptedConfig = remember(profile, learningStats) {
                    BrainlessPresets.adaptPreset(baseConfig, learningStats)
                }

                PoxCard(
                    onClick = {
                        activeProfile = profile
                        onApplyProfile(profile)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = when (profile) {
                                    BrainlessProfile.BATTERY_BACKUP -> MaterialTheme.colorScheme.tertiaryContainer
                                    BrainlessProfile.STABILITY -> MaterialTheme.colorScheme.primaryContainer
                                    BrainlessProfile.PERFORMANCE -> MaterialTheme.colorScheme.errorContainer
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = profile.icon,
                                        contentDescription = null,
                                        tint = when (profile) {
                                            BrainlessProfile.BATTERY_BACKUP -> MaterialTheme.colorScheme.onTertiaryContainer
                                            BrainlessProfile.STABILITY -> MaterialTheme.colorScheme.onPrimaryContainer
                                            BrainlessProfile.PERFORMANCE -> MaterialTheme.colorScheme.onErrorContainer
                                        },
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = profile.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isActive) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text(
                                                text = "ACTIVE & BOUND",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = profile.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            RadioButton(
                                selected = isActive,
                                onClick = {
                                    activeProfile = profile
                                    onApplyProfile(profile)
                                }
                            )
                        }

                        Text(
                            text = profile.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Highlights chips
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            baseConfig.highlights.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        if (adaptedConfig.isAdapted) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoMode,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = adaptedConfig.adaptationNotes.firstOrNull() ?: "Tuned by Silicon Calibration",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Info Banner
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
                        text = "Continuous Self-Tuning Active",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Brainless mode monitors your battery resistance, thermal gradient, and voltage sag to continuously adapt operating frequencies and charging limits without manual intervention.",
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
