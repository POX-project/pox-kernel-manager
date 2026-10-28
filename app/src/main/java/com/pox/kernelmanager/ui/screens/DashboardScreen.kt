package com.pox.kernelmanager.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pox.kernelmanager.BuildConfig
import com.pox.kernelmanager.ui.components.ChargingDetailCard
import com.pox.kernelmanager.ui.components.PoxTopAppBar
import com.pox.kernelmanager.ui.components.SectionHeader
import com.pox.kernelmanager.ui.components.StatCard
import com.pox.kernelmanager.ui.components.StatusBadge
import com.pox.kernelmanager.ui.viewmodel.DashboardViewModel

enum class DashboardDetailType {
    KERNEL_INFO,
    BATTERY_INFO,
    CPU_INFO,
    GPU_MEMORY_INFO
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var activeDetail by remember { mutableStateOf<DashboardDetailType?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner.lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onAppForegrounded()
                Lifecycle.Event.ON_PAUSE -> viewModel.onAppBackgrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.startHardwareMonitorLoop()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onAppBackgrounded()
        }
    }

    Scaffold(
        topBar = {
            PoxTopAppBar(
                title = "Pox Kernel",
                onRefresh = { viewModel.refresh() },
                isRefreshing = uiState.isLoading,
                onShare = { viewModel.shareKernelInfo() }
            )
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            // Hero Card: Kernel & Device Overview
            Card(
                onClick = { activeDetail = DashboardDetailType.KERNEL_INFO },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    MaterialTheme.colorScheme.surfaceContainer
                                )
                            )
                        )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Pox Kernel",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = uiState.deviceInfo.kernelEdition.ifEmpty { "Mainline" },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = BuildConfig.VERSION_NAME,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = uiState.deviceInfo.kernelRelease.ifEmpty { "4.14.357-Pox" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (uiState.deviceInfo.commitHash.isNotBlank()) {
                            Text(
                                text = "Commit ${uiState.deviceInfo.commitHash}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Text(
                            text = "${uiState.deviceInfo.deviceModel} (${uiState.deviceInfo.codename}) • ${uiState.deviceInfo.socName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        if (uiState.deviceInfo.rootProviderVersion.isNotBlank()) {
                            Text(
                                text = uiState.deviceInfo.rootProviderVersion,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatusBadge(
                                text = uiState.deviceInfo.rootProvider.displayName,
                                isActive = uiState.deviceInfo.rootProvider.isRooted &&
                                        uiState.deviceInfo.rootGranted,
                                activeColor = MaterialTheme.colorScheme.primary
                            )
                            StatusBadge(
                                text = "SELinux: ${uiState.deviceInfo.selinuxStatus}",
                                isActive = uiState.deviceInfo.selinuxStatus.equals("Enforcing", true),
                                activeColor = MaterialTheme.colorScheme.primary
                            )
                            StatusBadge(
                                text = if (uiState.deviceInfo.noMountActive) "NoMount Active" else "NoMount Inactive",
                                isActive = uiState.deviceInfo.noMountActive,
                                activeColor = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Tap for Advanced Kernel Specs →",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Section: Battery & Charging Telemetry
            SectionHeader(
                title = "Battery & Charging Telemetry",
                subtitle = "Hardware battery state, charging rates & direct bypass"
            )
            ChargingDetailCard(
                stats = uiState.stats,
                onClick = { activeDetail = DashboardDetailType.BATTERY_INFO }
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                StatusBadge(
                    text = if (uiState.stats.isBypassActive) "Bypass: Active" else "Direct Power: Ready",
                    isActive = uiState.stats.isBypassActive || uiState.stats.isCharging,
                    activeColor = MaterialTheme.colorScheme.primary
                )
                StatusBadge(
                    text = if (uiState.stats.isFastCharging) "Fast Charge: Active" else "Charge Guard: Normal",
                    isActive = uiState.stats.isFastCharging,
                    activeColor = MaterialTheme.colorScheme.secondary
                )
                StatusBadge(
                    text = "Battery Health: ${uiState.stats.batteryHealth}",
                    isActive = uiState.stats.batteryHealth.equals("Good", true),
                    activeColor = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section: CPU Complex
            SectionHeader(
                title = "CPU & Schedutil Complex",
                subtitle = "2x Cortex-A76 Big Cores + 6x Cortex-A55 Little Cores"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val maxBigFreq = 2050
                val maxLittleFreq = 2000
                val bigCoreFreq = uiState.stats.cpuCores.getOrNull(6)?.currentFreqMhz ?: maxBigFreq
                val littleCoreFreq = uiState.stats.cpuCores.getOrNull(0)?.currentFreqMhz ?: 1400

                StatCard(
                    title = "Cortex-A76 (Big)",
                    value = "${bigCoreFreq} MHz",
                    icon = Icons.Default.Speed,
                    subtitle = "Core 6, 7 • Max $maxBigFreq MHz",
                    progress = (bigCoreFreq / maxBigFreq.toFloat()).coerceIn(0f, 1f),
                    onClick = { activeDetail = DashboardDetailType.CPU_INFO },
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Cortex-A55 (Little)",
                    value = "${littleCoreFreq} MHz",
                    icon = Icons.Default.Memory,
                    subtitle = "Governor: ${uiState.stats.cpuGovernor}",
                    progress = (littleCoreFreq / maxLittleFreq.toFloat()).coerceIn(0f, 1f),
                    onClick = { activeDetail = DashboardDetailType.CPU_INFO },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section: GPU & Memory Compression
            SectionHeader(
                title = "Mali-G76 GPU & ZRAM Engine",
                subtitle = "Bifrost 4-Core GPU and iOS-style compressed RAM cushion"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "Mali-G76 MC4",
                    value = "${uiState.stats.gpuFreqMhz} MHz",
                    icon = Icons.Default.Games,
                    subtitle = "Load: ${uiState.stats.gpuLoadPercent}%",
                    progress = uiState.stats.gpuLoadPercent / 100f,
                    onClick = { activeDetail = DashboardDetailType.GPU_MEMORY_INFO },
                    modifier = Modifier.weight(1f)
                )
                val ramUsed = uiState.stats.ramUsedMb
                val ramTotal = uiState.stats.ramTotalMb.coerceAtLeast(1)
                StatCard(
                    title = "RAM / ZRAM",
                    value = "${ramUsed / 1024}G / ${ramTotal / 1024}G",
                    icon = Icons.Default.Storage,
                    subtitle = "ZRAM: ${uiState.stats.zramUsedMb}M (${String.format("%.2f", uiState.stats.zramCompRatio)}x)",
                    progress = ramUsed.toFloat() / ramTotal.toFloat(),
                    onClick = { activeDetail = DashboardDetailType.GPU_MEMORY_INFO },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }

        // Advanced Modal BottomSheet for Kernel & Hardware Details
        activeDetail?.let { detailType ->
            ModalBottomSheet(
                onDismissRequest = { activeDetail = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (detailType) {
                        DashboardDetailType.KERNEL_INFO -> {
                            Text(
                                text = "Kernel & Hardware Architecture",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            DetailRow("Release (UTS_RELEASE)", uiState.deviceInfo.kernelRelease)
                            DetailRow("Architecture", "ARM64 (aarch64 v8.2-A)")
                            DetailRow("SoC Platform", uiState.deviceInfo.socName)
                            DetailRow("Device Codename", "begonia / begoniain")
                            DetailRow("Variant", uiState.deviceInfo.kernelEdition)
                            DetailRow("Commit", uiState.deviceInfo.commitHash.ifBlank { "unknown" })
                            DetailRow("SELinux Policy", uiState.deviceInfo.selinuxStatus)
                            DetailRow("Root Solution", uiState.deviceInfo.rootProvider.displayName)
                            DetailRow("Sultan Debloat", "Active (CONFIG_SULTAN_DEBLOAT)")
                            DetailRow("Dynamic Fsync", "Active (/proc/perfmgr/dynamic_fsync)")
                            DetailRow("Wakelock Shield", "Active (/proc/perfmgr/wakelock_blocker)")
                        }

                        DashboardDetailType.BATTERY_INFO -> {
                            Text(
                                text = "Battery & Charging Subsystem",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            DetailRow("Battery Level", "${uiState.stats.batteryLevel}%")
                            DetailRow("Battery Temperature", "${uiState.stats.batteryTempC}°C")
                            DetailRow("Battery Voltage", "${uiState.stats.batteryVoltageMv} mV")
                            DetailRow("Instant Current", "${uiState.stats.batteryCurrentMa} mA")
                            DetailRow("Power Consumption / Rate", "${String.format("%.2f", uiState.stats.batteryWattageW)} W")
                            DetailRow("Charging State", if (uiState.stats.isCharging) "Charging" else "Discharging")
                            DetailRow("Hardware Bypass Mode", if (uiState.stats.isBypassActive) "ACTIVE (Power Port Only)" else "Inactive")
                            DetailRow("Proc Battery Status", "/proc/perfmgr/battery_status")
                            DetailRow("Bypass Node", "/sys/kernel/battery_protection/bypass_mode")
                            DetailRow("Thermal Guard Trip", "/sys/kernel/battery_protection/temp_limit")
                            DetailRow("PC USB SDP Limit", "Locked at 500mA (Motherboard Guard)")
                        }

                        DashboardDetailType.CPU_INFO -> {
                            Text(
                                text = "CPU Complex & Scheduler",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            DetailRow("Architecture", "Octa-Core Big.LITTLE (2x A76 + 6x A55)")
                            DetailRow("Active Governor", uiState.stats.cpuGovernor)
                            DetailRow("Schedutil Tune", "/sys/devices/system/cpu/cpufreq/schedutil")
                            DetailRow("BORE Scheduler", "Burst-Oriented Response Enhancer Active")
                            DetailRow("EAS Energy Model", "WALT & C-State Aware Active")
                            DetailRow("OPP Voltage Guard", "Armed • Brownout & Freeze Shield Active")
                            uiState.stats.cpuCores.forEach { core ->
                                val clusterName = if (core.isBigCore) "Big A76" else "LITTLE A55"
                                DetailRow("Core ${core.coreId} ($clusterName)", "${core.currentFreqMhz} MHz (${core.minFreqMhz}-${core.maxFreqMhz} MHz)")
                            }
                        }

                        DashboardDetailType.GPU_MEMORY_INFO -> {
                            Text(
                                text = "GPU & Memory Compression",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            DetailRow("GPU Model", "ARM Mali-G76 MC4 (Bifrost)")
                            DetailRow("GPU Frequency", "${uiState.stats.gpuFreqMhz} MHz")
                            DetailRow("GPU Active Load", "${uiState.stats.gpuLoadPercent}%")
                            DetailRow("Physical RAM", "${uiState.stats.ramUsedMb} MB / ${uiState.stats.ramTotalMb} MB")
                            DetailRow("ZRAM Cushion", "${uiState.stats.zramUsedMb} MB")
                            DetailRow("Compression Ratio", "${String.format("%.2f", uiState.stats.zramCompRatio)}x (zstd-percpu)")
                            DetailRow("VM Swappiness", "/proc/sys/vm/swappiness (Tuned: 100)")
                            DetailRow("VFS Cache Pressure", "/proc/sys/vm/vfs_cache_pressure (Tuned: 100)")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { activeDetail = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close Details")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.2f, fill = false)
        )
    }
}
