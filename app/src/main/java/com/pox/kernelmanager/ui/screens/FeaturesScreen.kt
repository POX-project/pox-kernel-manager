package com.pox.kernelmanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pox.kernelmanager.data.model.CustomColorProfile
import com.pox.kernelmanager.data.model.CustomKprofile
import com.pox.kernelmanager.data.model.TuningFeature
import com.pox.kernelmanager.data.model.GamingModeLevel
import com.pox.kernelmanager.data.model.KprofilesLevel
import com.pox.kernelmanager.data.model.HealthCalibrationMode
import com.pox.kernelmanager.ui.components.*
import com.pox.kernelmanager.ui.viewmodel.FeaturesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturesScreen(
    viewModel: FeaturesViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Root can be granted, or a module installed, while the app is backgrounded,
    // so re-probe on resume or the stale result leaves everything greyed out.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner.lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onAppForegrounded()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        FeaturesScreenContent(
            viewModel = viewModel,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Composable
fun FeaturesScreenContent(
    viewModel: FeaturesViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var showCreateKprofileDialog by remember { mutableStateOf(false) }
    var showCreateColorDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            RootStatusBanner(
                granted = uiState.features.rootGranted,
                providerLabel = uiState.features.rootProviderLabel
            )

            // 1. Kprofiles & Gaming Governor
            SectionHeader(
                title = "Kprofiles & Gaming Performance Governor",
                subtitle = "In-kernel profile modes (/sys/kernel/kprofiles/kp_mode) & MediaTek perfmgr"
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Kprofiles Mode (kp_mode)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Mode ${uiState.features.kprofilesMode.value}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        KprofilesLevel.entries.forEachIndexed { index, level ->
                            SegmentedButton(
                                selected = uiState.features.kprofilesMode == level,
                                onClick = { viewModel.setKprofilesMode(level) },
                                enabled = uiState.features.isSupported(TuningFeature.KPROFILES) || uiState.features.isSupported(TuningFeature.GAMING_MODE),
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = KprofilesLevel.entries.size)
                            ) {
                                Text(
                                    text = when (level) {
                                        KprofilesLevel.DISABLED -> "None"
                                        KprofilesLevel.BATTERY -> "Battery"
                                        KprofilesLevel.BALANCED -> "Balanced"
                                        KprofilesLevel.PERFORMANCE -> "Perf"
                                        KprofilesLevel.CUSTOM -> "Custom"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.features.kprofilesMode.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FeatureSwitchCard(
                title = "Auto-KP Screen-Off Saver",
                description = "Automatically forces Battery profile (Mode 1) on screen lock and restores profile upon display wake (/sys/kernel/kprofiles/auto_kp)",
                isChecked = uiState.features.autoKpEnabled,
                onCheckedChange = { viewModel.setAutoKp(it) },
                icon = Icons.Default.BrightnessAuto,
                enabled = uiState.features.isSupported(TuningFeature.KPROFILES) || uiState.features.isSupported(TuningFeature.GAMING_MODE)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Custom Kprofiles & Presets",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "One-tap user-tailored kernel tuning presets",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = { showCreateKprofileDialog = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Create", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    uiState.customKprofiles.forEach { profile ->
                        val isActive = uiState.features.kprofilesMode == KprofilesLevel.CUSTOM &&
                            uiState.features.activeKprofileId == profile.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = profile.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (profile.isPreset) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    text = "PRESET",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (isActive) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primary
                                            ) {
                                                Text(
                                                    text = "ACTIVE",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (profile.description.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = profile.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!profile.isPreset) {
                                        IconButton(
                                            onClick = { viewModel.deleteCustomKprofile(profile.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = { viewModel.applyCustomKprofile(profile) },
                                        enabled = !isActive,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(if (isActive) "Active" else "Apply", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

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
                        text = "Active POX Gaming Level",
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
                                enabled = uiState.features.isSupported(TuningFeature.GAMING_MODE),
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = GamingModeLevel.entries.size)
                            ) {
                                Text(
                                    text = when (level) {
                                        GamingModeLevel.POWERSAVE -> "Powersave"
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
                        text = if (uiState.features.isSupported(TuningFeature.GAMING_MODE)) {
                            uiState.features.gamingMode.description
                        } else {
                            "/proc/perfmgr is unavailable - grant root to enable"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FeatureSwitchCard(
                title = "FPSGO Touch Boost",
                description = "Raises the target OPP on touch via /proc/perfmgr/tchbst/user/usrtch",
                isChecked = uiState.features.touchBoost240Hz,
                onCheckedChange = { viewModel.setTouchBoost(it) },
                icon = Icons.Default.TouchApp,
                enabled = uiState.features.isSupported(TuningFeature.TOUCH_BOOST)
            )

            // 2. Phone Health & Silicon Table Calibration
            SectionHeader(
                title = "Phone Health & Silicon Table Calibration",
                subtitle = "Battery degradation adaptation, zero-brownout OPP guard & anti-freeze watchdog"
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Silicon Health Calibration Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Default.HealthAndSafety,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Dynamic OPP voltage droop compensation: calibrates big-core frequency ceilings to real battery internal resistance and temperature to eliminate voltage sags and PMIC UVLO panics.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        HealthCalibrationMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = uiState.features.healthCalibrationMode == mode,
                                onClick = { viewModel.setHealthCalibrationMode(mode) },
                                enabled = uiState.features.isSupported(TuningFeature.PHONE_HEALTH_CALIBRATION) || uiState.features.isSupported(TuningFeature.GAMING_MODE),
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = HealthCalibrationMode.entries.size)
                            ) {
                                Text(
                                    text = when (mode) {
                                        HealthCalibrationMode.DISABLED -> "Off"
                                        HealthCalibrationMode.AUTO_ADAPTIVE -> "Adaptive"
                                        HealthCalibrationMode.PERF_GUARD -> "Guard"
                                        HealthCalibrationMode.LONGEVITY -> "Longevity"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = uiState.features.healthCalibrationMode.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.features.healthStatusSummary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            FeatureSwitchCard(
                title = "OPP Voltage Brownout Guard & Anti-Freeze",
                description = "Prevents Cortex-A76 big core voltage collapse on degraded or low cells, elevating Little A55 schedutil up-ramp to guarantee 0 UI freezes",
                isChecked = uiState.features.oppVoltageGuard,
                onCheckedChange = { viewModel.setOppVoltageGuard(it) },
                icon = Icons.Default.Shield,
                enabled = uiState.features.isSupported(TuningFeature.PHONE_HEALTH_CALIBRATION) || uiState.features.isSupported(TuningFeature.GAMING_MODE)
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
                        text = "Touch Sensitivity & Response Rate",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val levels = listOf("Normal", "Gaming", "Sensitive", "Ultra")
                        levels.forEachIndexed { index, name ->
                            SegmentedButton(
                                selected = uiState.features.touchSensitivity == index,
                                onClick = { viewModel.setTouchSensitivity(index) },
                                enabled = uiState.features.isSupported(TuningFeature.TOUCH_SENSITIVITY),
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = levels.size)
                            ) {
                                Text(name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 2. Battery & Direct Bypass
            SectionHeader(
                title = "Smart Battery & Bypass Charging",
                subtitle = "Direct hardware bypass & battery longevity ceilings"
            )

            FeatureSwitchCard(
                title = "Hardware Fast Charging",
                description = "Hardware fast charging negotiation with custom wattage locking (/proc/perfmgr/fast_charge)",
                isChecked = uiState.features.fastChargeEnabled,
                onCheckedChange = { viewModel.toggleFastCharge(it) },
                icon = Icons.Default.FlashOn,
                enabled = uiState.features.isSupported(TuningFeature.FAST_CHARGE)
            )

            if (uiState.features.fastChargeEnabled) {
                var wattInputText by remember(uiState.features.chargeWatt) {
                    mutableStateOf(uiState.features.chargeWatt.toString())
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Charging Wattage Locking",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${uiState.features.chargeWatt}W Locked",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = "Locks charging power to this exact wattage on dedicated AC/wall chargers (DCP/PE). On USB-PD, requests matching power profile.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = wattInputText,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() }.take(3)
                                wattInputText = filtered
                                val parsed = filtered.toIntOrNull()
                                if (parsed != null && parsed in 5..120) {
                                    viewModel.setChargeWatt(parsed)
                                }
                            },
                            label = { Text("Target Wattage (5W - 90W+)") },
                            suffix = { Text("W") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "Quick Presets",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(10, 18, 30, 45, 67, 90).forEach { preset ->
                                FilterChip(
                                    selected = uiState.features.chargeWatt == preset,
                                    onClick = {
                                        wattInputText = preset.toString()
                                        viewModel.setChargeWatt(preset)
                                    },
                                    label = { Text("${preset}W", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        Text(
                            text = "USB-PD Negotiation Limits",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Sets adapter negotiation boundaries when connected to a USB-PD charger.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "Min PD: ${uiState.features.pdMinWatt}W",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(5, 9, 15, 18).forEach { preset ->
                                FilterChip(
                                    selected = uiState.features.pdMinWatt == preset,
                                    onClick = { viewModel.setPdMinWatt(preset) },
                                    label = { Text("${preset}W", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        Text(
                            text = "Max PD: ${uiState.features.pdMaxWatt}W",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(18, 30, 45, 65, 90).forEach { preset ->
                                FilterChip(
                                    selected = uiState.features.pdMaxWatt == preset,
                                    onClick = { viewModel.setPdMaxWatt(preset) },
                                    label = { Text("${preset}W", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "⚡ Non-PD Chargers: Power is locked strictly at ${uiState.features.chargeWatt}W.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "🛡️ PC Host Ports: Locked at 500mA (USB 2.0 SDP) to prevent motherboard port trip.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            FeatureSwitchCard(
                title = "Direct-Power Bypass Mode",
                description = "Power phone directly via charger port, halting battery heating and degradation during gaming or heavy workloads",
                isChecked = uiState.features.bypassCharging,
                onCheckedChange = { viewModel.toggleBypassCharging(it) },
                icon = Icons.Default.ElectricBolt,
                enabled = uiState.features.isSupported(TuningFeature.BYPASS_CHARGING)
            )

            FeatureSliderCard(
                title = "Charging Stop Limit",
                valueLabel = "${uiState.features.chargeLimitPercent}%",
                description = "Stops charging when battery reaches this threshold to prolong lifespan",
                value = uiState.features.chargeLimitPercent.toFloat(),
                valueRange = 50f..100f,
                steps = 9,
                onValueChange = { viewModel.setChargeLimit(it.toInt()) },
                enabled = uiState.features.isSupported(TuningFeature.CHARGE_LIMIT)
            )

            // Quick preset chips for Charge Limit
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(65, 80, 90, 100).forEach { preset ->
                    FilterChip(
                        selected = uiState.features.chargeLimitPercent == preset,
                        onClick = { viewModel.setChargeLimit(preset) },
                        label = { Text("${preset}% Cap", style = MaterialTheme.typography.labelSmall) },
                        enabled = uiState.features.isSupported(TuningFeature.CHARGE_LIMIT)
                    )
                }
            }

            FeatureSwitchCard(
                title = "Hardware Thermal Guard",
                description = "Clamps charging current when battery temperature exceeds ${uiState.features.tempLimitC}°C",
                isChecked = uiState.features.thermalGuardEnabled,
                onCheckedChange = { viewModel.toggleThermalGuard(it, uiState.features.tempLimitC) },
                icon = Icons.Default.Thermostat,
                enabled = uiState.features.isSupported(TuningFeature.THERMAL_GUARD)
            )

            UnsupportedReason(uiState.features.reasonFor(TuningFeature.THERMAL_GUARD))

            // 3. Media & Camera Studio
            SectionHeader(
                title = "Media & Camera Studio",
                subtitle = "Hardware 4K60 unlock, Sony S-Log3 cinema profile & torch overdrive"
            )

            FeatureSwitchCard(
                title = "Camera 4K60 FPS Mode",
                description = "Unlocks 4K 60 frames-per-second recording pipeline (/proc/perfmgr/camera_4k60)",
                isChecked = uiState.features.camera4k60Enabled,
                onCheckedChange = { viewModel.toggleCamera4k60(it) },
                icon = Icons.Default.Videocam,
                enabled = uiState.features.isSupported(TuningFeature.CAMERA_4K60)
            )

            FeatureSwitchCard(
                title = "Sony S-Log3 Cinema Flat",
                description = "Sony cinema logarithmic gamma curve for color grading in DaVinci Resolve (/proc/perfmgr/slog3)",
                isChecked = uiState.features.slog3Enabled,
                onCheckedChange = { viewModel.toggleSlog3(it) },
                icon = Icons.Default.Movie,
                enabled = uiState.features.isSupported(TuningFeature.CAMERA_SLOG3)
            )

            val torchLevel = uiState.features.torchBrightness.coerceIn(0, 10)
            val torchLabel = when (torchLevel) {
                0 -> "Off"
                1 -> "Level 1 (25mA • Bedside)"
                2 -> "Level 2 (50mA)"
                3 -> "Level 3 (75mA)"
                4 -> "Level 4 (100mA • Stock)"
                5 -> "Level 5 (137mA)"
                6 -> "Level 6 (175mA • High)"
                7 -> "Level 7 (212mA)"
                8 -> "Level 8 (250mA • Very High)"
                9 -> "Level 9 (287mA)"
                10 -> "Level 10 (325mA • Max Overdrive)"
                else -> "Level $torchLevel/10"
            }

            FeatureSliderCard(
                title = "BegoTorch Hardware Overdrive",
                valueLabel = torchLabel,
                description = "Direct MT6360 PMIC 10-step graded torch current (/proc/perfmgr/torch_brightness clamped at 325mA)",
                value = torchLevel.toFloat(),
                valueRange = 0f..10f,
                steps = 9,
                onValueChange = { viewModel.setTorchBrightness(it.toInt()) },
                enabled = uiState.features.isSupported(TuningFeature.TORCH_BRIGHTNESS)
            )

            // 4. Network & Tethering
            SectionHeader(
                title = "Network & Tethering",
                subtitle = "High-speed TCP pacing & tethering hotspot uncap"
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
                        text = "TCP Congestion Algorithm",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Kernel TCP pacing engine (/proc/sys/net/ipv4/tcp_congestion_control)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                    )
                    val available = if (uiState.features.availableTcpCongestions.isNotEmpty()) {
                        uiState.features.availableTcpCongestions
                    } else listOf("bbr", "westwood", "cubic")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        available.take(4).forEachIndexed { index, algo ->
                            SegmentedButton(
                                selected = uiState.features.tcpCongestion.equals(algo, ignoreCase = true),
                                onClick = { viewModel.setTcpCongestion(algo) },
                                enabled = uiState.features.isSupported(TuningFeature.TCP_CONGESTION),
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = available.take(4).size)
                            ) {
                                Text(algo.uppercase(), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            FeatureSwitchCard(
                title = "Tethering TTL 64 / HL 64 Hotspot Uncap",
                description = "Applies netfilter mangle rules to bypass carrier hotspot tethering throttle",
                isChecked = uiState.features.ttl64Enabled,
                onCheckedChange = { viewModel.toggleTtl64(it) },
                icon = Icons.Default.WifiTethering,
                enabled = uiState.features.isSupported(TuningFeature.TTL_64)
            )

            // 5. Advanced Kernel Scheduler & BORE
            SectionHeader(
                title = "Advanced Kernel Scheduler & BORE",
                subtitle = "BORE burst response, WALT tracking & C-state awareness"
            )

            FeatureSwitchCard(
                title = "BORE Burst Response Enhancer",
                description = "Tracks task burst execution time across sleeps/yields, penalizing CPU-bound background hogs to guarantee zero frame drops on UI threads (kernel.sched_bore)",
                isChecked = uiState.features.schedBoreEnabled,
                onCheckedChange = { viewModel.setSchedBore(it) },
                icon = Icons.Default.Bolt,
                enabled = uiState.features.isSupported(TuningFeature.BORE_SCHEDULER) || uiState.features.rootGranted
            )

            FeatureSliderCard(
                title = "BORE Burst Penalty Scale",
                valueLabel = "${uiState.features.schedBurstPenaltyScale}",
                description = "Penalty multiplier (0-4095, default 1536) governing how rapidly continuous background tasks lose priority",
                value = uiState.features.schedBurstPenaltyScale.toFloat(),
                valueRange = 0f..4095f,
                steps = 15,
                onValueChange = { viewModel.setSchedBurstPenaltyScale(it.toInt()) },
                enabled = uiState.features.isSupported(TuningFeature.BORE_SCHEDULER) || uiState.features.rootGranted
            )

            FeatureSliderCard(
                title = "BORE Burst Penalty Offset",
                valueLabel = "${uiState.features.schedBurstPenaltyOffset}",
                description = "Baseline burst duration offset before penalty scaling engages (0-63, default 24)",
                value = uiState.features.schedBurstPenaltyOffset.toFloat(),
                valueRange = 0f..63f,
                steps = 14,
                onValueChange = { viewModel.setSchedBurstPenaltyOffset(it.toInt()) },
                enabled = uiState.features.isSupported(TuningFeature.BORE_SCHEDULER) || uiState.features.rootGranted
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
                        text = "BORE Child Penalty Inheritance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Controls how newly spawned fork/clone child tasks inherit burst penalties from parent threads (0=None, 1=Parent, 2=Topological Tree)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val inheritOptions = listOf("None", "Parent", "Tree")
                        inheritOptions.forEachIndexed { index, name ->
                            SegmentedButton(
                                selected = uiState.features.schedBurstInheritType == index,
                                onClick = { viewModel.setSchedBurstInheritType(index) },
                                enabled = uiState.features.isSupported(TuningFeature.BORE_SCHEDULER) || uiState.features.rootGranted,
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = inheritOptions.size)
                            ) {
                                Text(name, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            FeatureSwitchCard(
                title = "Power-Efficient Workqueues",
                description = "Groups unbound workqueues to avoid waking idle CPU cores (/sys/module/workqueue/parameters/power_efficient)",
                isChecked = uiState.features.wqPowerEfficient,
                onCheckedChange = { viewModel.toggleWqPowerEfficient(it) },
                icon = Icons.Default.BatterySaver,
                enabled = uiState.features.isSupported(TuningFeature.WORKQUEUE_POWER_EFFICIENT)
            )

            FeatureSwitchCard(
                title = "WALT CPU Utilization",
                description = "Uses Window-Assisted Load Tracking for faster CPU frequency ramp-up (/proc/sys/kernel/sched_use_walt_cpu_util)",
                isChecked = uiState.features.waltCpuUtil,
                onCheckedChange = { viewModel.toggleWaltCpuUtil(it) },
                icon = Icons.Default.Speed,
                enabled = uiState.features.isSupported(TuningFeature.WALT_CPU_UTIL)
            )

            FeatureSwitchCard(
                title = "WALT Task Utilization",
                description = "Uses WALT task load signals for task placement on optimal cores (/proc/sys/kernel/sched_use_walt_task_util)",
                isChecked = uiState.features.waltTaskUtil,
                onCheckedChange = { viewModel.toggleWaltTaskUtil(it) },
                icon = Icons.Default.Insights,
                enabled = uiState.features.isSupported(TuningFeature.WALT_TASK_UTIL)
            )

            FeatureSwitchCard(
                title = "C-State Aware Scheduling",
                description = "Prefers waking CPUs in shallower idle states to save energy and latency (/proc/sys/kernel/sched_cstate_aware)",
                isChecked = uiState.features.schedCstateAware,
                onCheckedChange = { viewModel.toggleSchedCstateAware(it) },
                icon = Icons.Default.PowerSettingsNew,
                enabled = uiState.features.isSupported(TuningFeature.SCHED_CSTATE_AWARE)
            )

            // 6. Memory & HyperOS xswapd Subsystem
            SectionHeader(
                title = "Memory Management & xswapd",
                subtitle = "VM swappiness, asynchronous per-memcg page swap & compressor"
            )

            FeatureSliderCard(
                title = "VM Swappiness",
                valueLabel = "${uiState.features.vmSwappiness}",
                description = "Aggressiveness of kernel page swapping to zRAM (/proc/sys/vm/swappiness)",
                value = uiState.features.vmSwappiness.toFloat(),
                valueRange = 0f..100f,
                steps = 19,
                onValueChange = { viewModel.setVmSwappiness(it.toInt()) },
                enabled = uiState.features.isSupported(TuningFeature.VM_SWAPPINESS)
            )

            FeatureSwitchCard(
                title = "xswapd Background Engine",
                description = "Offloads memory swap compression to unbound kernel workers, preventing binder stalls and launcher redraws",
                isChecked = uiState.features.xswapdEnabled,
                onCheckedChange = { viewModel.toggleXswapd(it) },
                icon = Icons.Default.Compress,
                enabled = uiState.features.isSupported(TuningFeature.XSWAPD)
            )

            UnsupportedReason(uiState.features.reasonFor(TuningFeature.XSWAPD))

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
                        enabled = uiState.features.isSupported(TuningFeature.XSWAPD),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Reclaim")
                    }
                }
            }

            // 7. Display, Touch, Audio & Haptics
            SectionHeader(
                title = "Display, Touch, Audio & Haptics",
                subtitle = "Calibrated color profiles, audio amplifier boost & tactile feedback"
            )

            FeatureSwitchCard(
                title = "Double Tap to Wake (DT2W)",
                description = "Wake screen by double tapping touch panel in deep sleep",
                isChecked = uiState.features.doubleTapToWake,
                onCheckedChange = { viewModel.toggleDt2w(it) },
                icon = Icons.Default.TouchApp,
                enabled = uiState.features.isSupported(TuningFeature.DT2W)
            )

            FeatureSwitchCard(
                title = "True Tone Liquid Retina D65",
                description = "Calibrated D65 color balance reference profile for begonia LCD (/proc/perfmgr/true_tone)",
                isChecked = uiState.features.trueToneDisplay,
                onCheckedChange = { viewModel.toggleTrueTone(it) },
                icon = Icons.Default.Palette,
                enabled = uiState.features.isSupported(TuningFeature.TRUE_TONE)
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
                        text = "Display Color Profile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val profiles = listOf("Standard", "D65 Ref", "Vivid", "SLog3", "Custom")
                        profiles.forEachIndexed { index, name ->
                            SegmentedButton(
                                selected = uiState.features.colorMode == index,
                                onClick = { viewModel.setColorMode(index) },
                                enabled = uiState.features.isSupported(TuningFeature.COLOR_MODE),
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = profiles.size)
                            ) {
                                Text(name, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Custom Color Profiles",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Hardware saturation, contrast & tone curve tables",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = { showCreateColorDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    uiState.customColorProfiles.forEach { profile ->
                        val isColorActive = uiState.features.colorMode == 4 &&
                            uiState.features.activeColorProfileId == profile.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isColorActive)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = profile.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (profile.isPreset) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    text = "PRESET",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp),
                                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "Sat: ${profile.saturation} | Con: ${profile.contrast} | Bri: ${profile.brightness} | Adj: ${profile.saturationAdjustment}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!profile.isPreset) {
                                        IconButton(
                                            onClick = { viewModel.deleteCustomColorProfile(profile.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = { viewModel.applyCustomColorProfile(profile) },
                                        enabled = !isColorActive,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text(if (isColorActive) "Active" else "Apply", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

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
                        text = "High Brightness Mode (HBM)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val hbmLevels = listOf("Off", "L1 Boost", "L2 High", "L3 Peak")
                        hbmLevels.forEachIndexed { index, name ->
                            SegmentedButton(
                                selected = uiState.features.hbmMode == index,
                                onClick = { viewModel.setHbmMode(index) },
                                enabled = uiState.features.isSupported(TuningFeature.HBM_MODE),
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = hbmLevels.size)
                            ) {
                                Text(name, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            FeatureSliderCard(
                title = "Headphone Analog Gain",
                valueLabel = "+${uiState.features.headphoneGainDb} dB",
                description = "Hardware audio amplifier gain boost for 3.5mm analog output (/proc/perfmgr/headphone_gain)",
                value = uiState.features.headphoneGainDb.toFloat(),
                valueRange = 0f..8f,
                steps = 7,
                onValueChange = { viewModel.setHeadphoneGain(it.toInt()) },
                enabled = uiState.features.isSupported(TuningFeature.HEADPHONE_GAIN)
            )

            FeatureSliderCard(
                title = "Microphone Analog Gain",
                valueLabel = "Level ${uiState.features.micGainLevel}",
                description = "Direct hardware microphone input sensitivity boost (/proc/perfmgr/mic_gain)",
                value = uiState.features.micGainLevel.toFloat(),
                valueRange = 0f..4f,
                steps = 3,
                onValueChange = { viewModel.setMicGain(it.toInt()) },
                enabled = uiState.features.isSupported(TuningFeature.MIC_GAIN)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Microphone",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            val activeLabel = when (uiState.features.activeMicMode) {
                                0 -> "Main (Bottom)"
                                1 -> "Top (Secondary)"
                                2 -> "Headset"
                                3 -> "Dual Mic"
                                else -> "Main"
                            }
                            Text(
                                text = activeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Text(
                        text = "Hardware microphone routing (/proc/perfmgr/active_mic & QS tile)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val micOptions = listOf("Main", "Top", "Headset", "Dual")
                        micOptions.forEachIndexed { index, name ->
                            SegmentedButton(
                                selected = uiState.features.activeMicMode == index,
                                onClick = { viewModel.setActiveMic(index) },
                                enabled = uiState.features.isSupported(TuningFeature.ACTIVE_MIC),
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = micOptions.size)
                            ) {
                                Text(name, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            FeatureSwitchCard(
                title = "Wakelock Blocker",
                description = "Aggressive kernel wakelock filter preventing rogue apps from keeping CPU awake in deep sleep",
                isChecked = uiState.features.wakelockBlockerEnabled,
                onCheckedChange = { viewModel.toggleWakelockBlocker(it) },
                icon = Icons.Default.Bedtime,
                enabled = uiState.features.isSupported(TuningFeature.WAKELOCK_BLOCKER)
            )

            FeatureSliderCard(
                title = "Hardware Vibrator Strength",
                valueLabel = "${uiState.features.vibratorStrengthPercent}%",
                description = "Unlocked 3.3V analog haptic motor voltage driving range",
                value = uiState.features.vibratorStrengthPercent.toFloat(),
                valueRange = 0f..100f,
                steps = 13,
                onValueChange = { viewModel.setVibratorStrength(it.toInt()) },
                enabled = uiState.features.isSupported(TuningFeature.VIBRATOR_STRENGTH)
            )

            // 8. Storage & Filesystem
            SectionHeader(
                title = "Storage & Dynamic Fsync",
                subtitle = "Micro-stutter elimination & UFS flash scheduler"
            )

            FeatureSwitchCard(
                title = "Dynamic Fsync",
                description = "Disables synchronous flush when screen is on, flushing immediately when screen goes off",
                isChecked = uiState.features.dynamicFsync,
                onCheckedChange = { viewModel.toggleDynamicFsync(it) },
                icon = Icons.Default.Storage,
                enabled = uiState.features.isSupported(TuningFeature.DYNAMIC_FSYNC)
            )

            UnsupportedReason(uiState.features.reasonFor(TuningFeature.DYNAMIC_FSYNC))

            FeatureSwitchCard(
                title = "DriveDroid USB CD-ROM Emulation",
                description = "Enables physical SCSI CD-ROM emulation on USB LUN0 to boot live ISO images on PCs via DriveDroid (/proc/perfmgr/usb_cdrom)",
                isChecked = uiState.features.driveDroidCdrom,
                onCheckedChange = { viewModel.toggleDriveDroidCdrom(it) },
                icon = Icons.Default.Album,
                enabled = uiState.features.isSupported(TuningFeature.DRIVEDROID)
            )

            UnsupportedReason(uiState.features.reasonFor(TuningFeature.DRIVEDROID))

            FeatureSliderCard(
                title = "UFS Queue Readahead",
                valueLabel = "${uiState.features.readAheadKb} KB",
                description = "Readahead buffer size for sequential flash throughput",
                value = uiState.features.readAheadKb.toFloat(),
                valueRange = 64f..512f,
                steps = 6,
                onValueChange = { viewModel.setReadAhead(it.toInt()) },
                enabled = uiState.features.isSupported(TuningFeature.READ_AHEAD)
            )
            UnsupportedReason(uiState.features.reasonFor(TuningFeature.READ_AHEAD))

            if (uiState.features.availableSchedulers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "I/O SCHEDULER",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(horizontal = 16.dp)) {
                    uiState.features.availableSchedulers.forEachIndexed { index, scheduler ->
                        SegmentedButton(
                            selected = uiState.features.ioScheduler == scheduler,
                            onClick = { viewModel.setIoScheduler(scheduler) },
                            enabled = uiState.features.isSupported(TuningFeature.IO_SCHEDULER),
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = uiState.features.availableSchedulers.size
                            )
                        ) {
                            Text(scheduler, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (uiState.features.availableGovernors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "CPU GOVERNOR",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(horizontal = 16.dp)) {
                    uiState.features.availableGovernors.forEachIndexed { index, governor ->
                        SegmentedButton(
                            selected = uiState.features.cpuGovernor == governor,
                            onClick = { viewModel.setCpuGovernor(governor) },
                            enabled = uiState.features.isSupported(TuningFeature.CPU_GOVERNOR),
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = uiState.features.availableGovernors.size
                            )
                        ) {
                            Text(governor, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                UnsupportedReason(uiState.features.reasonFor(TuningFeature.CPU_GOVERNOR))
            }

            if (uiState.features.isSupported(TuningFeature.ZRAM_SIZE)) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "ZRAM BACKING SIZE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                    listOf(128L, 256L, 512L, 1024L, 2048L).forEach { size ->
                        FilterChip(
                            selected = uiState.features.zramSizeMb == size,
                            onClick = { viewModel.setZramSizeMb(size) },
                            label = { Text("${size}M", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showCreateKprofileDialog) {
            CreateKprofileDialog(
                onDismiss = { showCreateKprofileDialog = false },
                onSave = { viewModel.saveCustomKprofile(it) }
            )
        }

        if (showCreateColorDialog) {
            CreateColorProfileDialog(
                onDismiss = { showCreateColorDialog = false },
                onSave = { viewModel.saveCustomColorProfile(it) }
            )
        }
    }
}

@Composable
private fun CreateKprofileDialog(
    onDismiss: () -> Unit,
    onSave: (CustomKprofile) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var gamingMode by remember { mutableStateOf(GamingModeLevel.BALANCED) }
    var touchGameMode by remember { mutableStateOf(false) }
    var touchSensitivity by remember { mutableIntStateOf(0) }
    var schedBore by remember { mutableStateOf(true) }
    var schedBurstScale by remember { mutableIntStateOf(1536) }
    var schedBurstOffset by remember { mutableIntStateOf(24) }
    /* Kernel custom_params tunables (gaming_mode.h tunables order). */
    var kpUpLittleUs by remember { mutableIntStateOf(500) }
    var kpDownLittleUs by remember { mutableIntStateOf(20000) }
    var kpUpBigUs by remember { mutableIntStateOf(200) }
    var kpDownBigUs by remember { mutableIntStateOf(20000) }
    var kpTopAppBoost by remember { mutableIntStateOf(20) }
    var kpFgBoost by remember { mutableIntStateOf(5) }
    var kpGpuBoost by remember { mutableStateOf(true) }
    var kpDramBoost by remember { mutableStateOf(false) }
    var kpRescueOpp by remember { mutableIntStateOf(0) }
    var dynamicFsync by remember { mutableStateOf(true) }
    var fastCharge by remember { mutableStateOf(true) }
    var healthCalibration by remember { mutableStateOf(HealthCalibrationMode.AUTO_ADAPTIVE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Custom Kprofile", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text("Governor & Gaming Level", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    GamingModeLevel.entries.forEachIndexed { index, level ->
                        SegmentedButton(
                            selected = gamingMode == level,
                            onClick = { gamingMode = level },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = GamingModeLevel.entries.size)
                        ) {
                            Text(
                                text = when (level) {
                                    GamingModeLevel.POWERSAVE -> "Save"
                                    GamingModeLevel.BALANCED -> "Bal"
                                    GamingModeLevel.GAMING -> "Game"
                                    GamingModeLevel.EXTREME -> "Ext"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("240Hz Touch Gaming Mode", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = touchGameMode, onCheckedChange = { touchGameMode = it })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Touch Sensitivity: $touchSensitivity", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = touchSensitivity.toFloat(),
                    onValueChange = { touchSensitivity = it.toInt() },
                    valueRange = 0f..3f,
                    steps = 2
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("BORE Scheduler", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = schedBore, onCheckedChange = { schedBore = it })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Burst Penalty Scale: $schedBurstScale", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = schedBurstScale.toFloat(),
                    onValueChange = { schedBurstScale = it.toInt() },
                    valueRange = 0f..4095f
                )
                Text("Burst Penalty Offset: $schedBurstOffset", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = schedBurstOffset.toFloat(),
                    onValueChange = { schedBurstOffset = it.toInt() },
                    valueRange = 0f..63f,
                    steps = 14
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("Custom Kprofile Tunables (kp_mode 4)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("A55 ramp-up: ${kpUpLittleUs}us", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = kpUpLittleUs.toFloat(),
                    onValueChange = { kpUpLittleUs = it.toInt() },
                    valueRange = 50f..5000f
                )
                Text("A55 ramp-down: ${kpDownLittleUs}us", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = kpDownLittleUs.toFloat(),
                    onValueChange = { kpDownLittleUs = it.toInt() },
                    valueRange = 500f..100000f
                )
                Text("A76 ramp-up: ${kpUpBigUs}us", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = kpUpBigUs.toFloat(),
                    onValueChange = { kpUpBigUs = it.toInt() },
                    valueRange = 50f..5000f
                )
                Text("A76 ramp-down: ${kpDownBigUs}us", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = kpDownBigUs.toFloat(),
                    onValueChange = { kpDownBigUs = it.toInt() },
                    valueRange = 500f..100000f
                )
                Text("Top-App Boost: $kpTopAppBoost%", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = kpTopAppBoost.toFloat(),
                    onValueChange = { kpTopAppBoost = it.toInt() },
                    valueRange = 0f..100f
                )
                Text("Foreground Boost: $kpFgBoost%", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = kpFgBoost.toFloat(),
                    onValueChange = { kpFgBoost = it.toInt() },
                    valueRange = 0f..100f
                )
                Text("Peak Rescue OPP Ceiling: $kpRescueOpp (0=peak)", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = kpRescueOpp.toFloat(),
                    onValueChange = { kpRescueOpp = it.toInt() },
                    valueRange = 0f..4f,
                    steps = 3
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("GPU Gaming Boost", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = kpGpuBoost, onCheckedChange = { kpGpuBoost = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Lock Max DRAM OPP", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = kpDramBoost, onCheckedChange = { kpDramBoost = it })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dynamic Fsync", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = dynamicFsync, onCheckedChange = { dynamicFsync = it })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("3.0A Fast Charge", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = fastCharge, onCheckedChange = { fastCharge = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            CustomKprofile(
                                name = name.trim(),
                                description = desc.trim(),
                                gamingMode = gamingMode,
                                touchGameMode = touchGameMode,
                                touchSensitivity = touchSensitivity,
                                schedBore = schedBore,
                                schedBurstPenaltyScale = schedBurstScale,
                                schedBurstPenaltyOffset = schedBurstOffset,
                                schedutilUpLittleUs = kpUpLittleUs,
                                schedutilDownLittleUs = kpDownLittleUs,
                                schedutilUpBigUs = kpUpBigUs,
                                schedutilDownBigUs = kpDownBigUs,
                                topAppBoost = kpTopAppBoost,
                                fgBoost = kpFgBoost,
                                gpuBoost = kpGpuBoost,
                                dramBoost = kpDramBoost,
                                rescueOpp = kpRescueOpp,
                                dynamicFsync = dynamicFsync,
                                fastCharge = fastCharge,
                                healthCalibration = healthCalibration
                            )
                        )
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CreateColorProfileDialog(
    onDismiss: () -> Unit,
    onSave: (CustomColorProfile) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var sat by remember { mutableIntStateOf(4) }
    var contrast by remember { mutableIntStateOf(4) }
    var brightness by remember { mutableIntStateOf(4) }
    var satAdj by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Color Profile", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text("Saturation: $sat (LUT step 0-9)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = sat.toFloat(),
                    onValueChange = { sat = it.toInt() },
                    valueRange = 0f..9f,
                    steps = 8
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Contrast: $contrast (LUT step 0-9)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = contrast.toFloat(),
                    onValueChange = { contrast = it.toInt() },
                    valueRange = 0f..9f,
                    steps = 8
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Brightness: $brightness (LUT step 0-9)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = brightness.toFloat(),
                    onValueChange = { brightness = it.toInt() },
                    valueRange = 0f..9f,
                    steps = 8
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Saturation Adjustment (Tone Curve): $satAdj (0-18)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = satAdj.toFloat(),
                    onValueChange = { satAdj = it.toInt() },
                    valueRange = 0f..18f,
                    steps = 17
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            CustomColorProfile(
                                name = name.trim(),
                                description = desc.trim(),
                                saturation = sat,
                                contrast = contrast,
                                brightness = brightness,
                                saturationAdjustment = satAdj
                            )
                        )
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
