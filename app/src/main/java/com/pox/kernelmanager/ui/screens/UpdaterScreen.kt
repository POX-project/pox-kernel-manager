package com.pox.kernelmanager.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pox.kernelmanager.data.model.ReleaseChannel
import com.pox.kernelmanager.data.model.UpdateCategory
import com.pox.kernelmanager.data.model.UpdateItem
import com.pox.kernelmanager.data.model.UpdateState
import com.pox.kernelmanager.ui.components.PoxTopAppBar
import com.pox.kernelmanager.ui.components.SectionHeader
import com.pox.kernelmanager.ui.components.StatusBadge
import com.pox.kernelmanager.ui.viewmodel.UpdaterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdaterScreen(
    viewModel: UpdaterViewModel,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val updateState by viewModel.updateState.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedChannel by viewModel.selectedChannel.collectAsState()
    val updateItems by viewModel.updateItems.collectAsState()
    val selectedItem by viewModel.selectedItem.collectAsState()
    val flashLogs by viewModel.flashLogs.collectAsState()
    val isFlashingActive by viewModel.isFlashingActive.collectAsState()
    val flashingSuccess by viewModel.flashingSuccess.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()

    // SAF File Picker for local ZIP flashing (KernelFlasher)
    val localZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.flashLocalZip(uri, context)
        }
    }

    Scaffold(
        topBar = {
            PoxTopAppBar(
                title = "Updates & Flasher",
                onRefresh = { viewModel.checkForUpdates() },
                isRefreshing = updateState is UpdateState.Checking,
                onBack = onBack
            )
        },
        containerColor = Color.Transparent,
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. Category Switcher (Kernel vs Pox Manager App)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedCategory == UpdateCategory.KERNEL,
                                onClick = { viewModel.setCategory(UpdateCategory.KERNEL) },
                                label = { Text("Kernel Updates") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DeveloperBoard,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            FilterChip(
                                selected = selectedCategory == UpdateCategory.APP_MANAGER,
                                onClick = { viewModel.setCategory(UpdateCategory.APP_MANAGER) },
                                label = { Text("Pox Manager App") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Android,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Channel Filter Chips (Releases vs Actions CI Builds)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedChannel == ReleaseChannel.STABLE_RELEASES,
                                onClick = { viewModel.setChannel(ReleaseChannel.STABLE_RELEASES) },
                                label = { Text("Official Releases") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                            FilterChip(
                                selected = selectedChannel == ReleaseChannel.ACTIONS_BUILDS,
                                onClick = { viewModel.setChannel(ReleaseChannel.ACTIONS_BUILDS) },
                                label = { Text("CI Test Builds (Artifacts)") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Build,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // 2. Status & Device Info Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
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
                                    text = if (selectedCategory == UpdateCategory.KERNEL) "Active Kernel Edition" else "Installed Companion App",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (selectedCategory == UpdateCategory.KERNEL) {
                                        deviceInfo.kernelEdition.ifBlank { "Linux 4.14 Mainline" }
                                    } else {
                                        "v${viewModel.currentAppVersion}"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            FilledTonalButton(
                                onClick = { viewModel.checkForUpdates() },
                                enabled = updateState !is UpdateState.Checking && updateState !is UpdateState.Downloading && !isFlashingActive,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Check")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (selectedCategory == UpdateCategory.KERNEL) {
                                "Target: ${deviceInfo.deviceModel} (${deviceInfo.codename}) • ${deviceInfo.rootProvider.displayName}"
                            } else {
                                "Repository: POX-project/pox-kernel-manager"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. KernelFlasher Local ZIP Flash Button (Only for Kernel Category)
            if (selectedCategory == UpdateCategory.KERNEL) {
                item {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { localZipLauncher.launch("application/zip") },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Flash Local Kernel ZIP",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Pick and install custom AnyKernel3 zip directly",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 4. Live Terminal Flash Viewer (KernelFlasher Style)
            if (isFlashingActive || flashLogs.isNotEmpty()) {
                item {
                    TerminalConsoleCard(
                        logs = flashLogs,
                        isActive = isFlashingActive,
                        isSuccess = flashingSuccess,
                        onRebootSystem = { viewModel.rebootSystem() },
                        onRebootRecovery = { viewModel.rebootToRecovery() },
                        onDismiss = { viewModel.clearFlashTerminal() }
                    )
                }
            }

            // 5. Active State Cards (Checking, Downloading, Completed, Error)
            when (val state = updateState) {
                is UpdateState.Checking -> {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "Querying GitHub ${selectedChannel.title}...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                is UpdateState.Downloading -> {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "Downloading ${state.packageName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { state.progressPercent / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${String.format("%.1f", state.downloadedMb)} MB / ${String.format("%.1f", state.totalMb)} MB (${state.progressPercent}%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "${String.format("%.0f", state.speedKbps)} KB/s",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
                is UpdateState.DownloadCompleted -> {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Package Downloaded & Verified",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = state.savedLocation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(14.dp))

                                if (state.file.name.endsWith(".apk") || selectedCategory == UpdateCategory.APP_MANAGER) {
                                    Button(
                                        onClick = { viewModel.installAppUpdate(state.file, context) },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Android, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Install Pox Manager Update")
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { viewModel.flashKernelDirectly(state.file) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.FlashOn, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Flash In-App")
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.stageDownloadedPackageForRecovery(state.file) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Recovery Stage")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is UpdateState.Error -> {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Notice",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = state.message, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
                else -> Unit
            }

            // 6. Section Header for Items
            item {
                SectionHeader(
                    title = if (selectedCategory == UpdateCategory.KERNEL) "Available Kernel Packages" else "Available App Releases",
                    subtitle = if (selectedChannel == ReleaseChannel.STABLE_RELEASES) {
                        "Official GitHub Releases with release notes & AnyKernel3 zips"
                    } else {
                        "Direct build artifacts generated from GitHub Actions CI runs"
                    }
                )
            }

            // 7. List of Update Items
            items(updateItems, key = { it.id }) { item ->
                val isSelected = selectedItem?.id == item.id
                UpdateItemCard(
                    item = item,
                    isSelected = isSelected,
                    onClick = { viewModel.selectItem(item) }
                )
            }

            // 8. Bottom Download / Action Bar
            item {
                if (selectedItem != null && updateState !is UpdateState.Downloading && !isFlashingActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.downloadSelectedItem() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download ${selectedItem?.tagOrRunNumber} (${String.format("%.1f", (selectedItem?.fileSizeBytes ?: 0L) / (1024f * 1024f))} MB)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateItemCard(
    item: UpdateItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = onClick
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.editionName.ifBlank { item.tagOrRunNumber },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (item.isRecommended) {
                            StatusBadge(text = "Recommended", isActive = true)
                        }
                        if (item.branch != null) {
                            AssistChip(
                                onClick = {},
                                label = { Text(item.branch, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.commitSha != null) {
                        Text(
                            text = "Commit ${item.commitSha}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Text(
                        text = "• ${String.format("%.1f", item.fileSizeBytes / (1024f * 1024f))} MB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (item.publishedAt != null) {
                    val dateFormatted = item.publishedAt.take(10)
                    Text(
                        text = dateFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (!item.changelog.isNullOrBlank() && isSelected) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = item.changelog.trim().take(300),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun TerminalConsoleCard(
    logs: List<String>,
    isActive: Boolean,
    isSuccess: Boolean?,
    onRebootSystem: () -> Unit,
    onRebootRecovery: () -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = if (isActive) MaterialTheme.colorScheme.primary else Color(0xFF64B5F6)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Kernel Flasher Console",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                when {
                    isActive -> {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    }
                    isSuccess == true -> {
                        StatusBadge(text = "Flash Succeeded", isActive = true)
                    }
                    isSuccess == false -> {
                        StatusBadge(text = "Flash Failed", isActive = false)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Console Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 280.dp)
                    .background(Color(0xFF070A0F), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(logs) { line ->
                        val lineColor = when {
                            line.contains("error", true) || line.contains("failed", true) -> Color(0xFFFF5252)
                            line.contains("success", true) || line.contains("complete", true) || line.contains("done", true) -> Color(0xFF69F0AE)
                            line.contains("AnyKernel", true) || line.contains("Pox", true) -> Color(0xFF40C4FF)
                            else -> Color(0xFFECEFF1)
                        }
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            ),
                            color = lineColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Console Action Buttons
            if (isSuccess == true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onRebootSystem,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reboot System")
                    }
                    OutlinedButton(
                        onClick = onRebootRecovery,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Recovery")
                    }
                }
            } else if (isSuccess == false || (!isActive && logs.isNotEmpty())) {
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Dismiss Console")
                }
            }
        }
    }
}
