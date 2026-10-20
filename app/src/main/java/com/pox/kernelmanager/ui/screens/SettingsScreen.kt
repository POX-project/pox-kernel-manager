package com.pox.kernelmanager.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pox.kernelmanager.data.learning.AdaptiveLearningManager
import com.pox.kernelmanager.data.repo.TuningRepository
import com.pox.kernelmanager.ui.components.PoxTopAppBar
import com.pox.kernelmanager.ui.components.SectionHeader
import com.pox.kernelmanager.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToUpdater: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentTheme = ThemeManager.currentThemeState
    val bgConfig = ThemeManager.backgroundConfigState
    val tokens = LocalThemeTokens.current

    var learningStats by remember { mutableStateOf(AdaptiveLearningManager.getStats()) }
    var isCalibrating by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            ThemeManager.setBackgroundImageUri(it.toString())
            ThemeManager.setBackgroundType(BackgroundType.CUSTOM_IMAGE)
            scope.launch {
                snackbarHostState.showSnackbar("Custom wallpaper applied")
            }
        }
    }

    Scaffold(
        topBar = {
            PoxTopAppBar(
                title = "Settings & Appearance"
            )
        },
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(bottom = 32.dp)
        ) {
            // Section 1: Themes System
            SectionHeader(
                title = "Design Themes",
                subtitle = "Choose from 4 radically distinct design identities"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Apple Liquid Glass Card Preview
                ThemePreviewCard(
                    theme = AppTheme.APPLE_LIQUID_GLASS,
                    isSelected = currentTheme == AppTheme.APPLE_LIQUID_GLASS,
                    shape = RoundedCornerShape(26.dp),
                    background = Color(0xFF131D31).copy(alpha = 0.55f),
                    border = BorderStroke(
                        1.2.dp,
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.40f),
                                Color.White.copy(alpha = 0.08f),
                                Color(0xFF0A84FF).copy(alpha = 0.35f)
                            )
                        )
                    ),
                    accentColor = Color(0xFF0A84FF),
                    tagText = "iOS Glassmorphism • Frosted Specular Blur",
                    onSelect = { ThemeManager.setTheme(AppTheme.APPLE_LIQUID_GLASS) }
                )

                // 2. Material 3 Expressive Card Preview
                ThemePreviewCard(
                    theme = AppTheme.MATERIAL3_EXPRESSIVE,
                    isSelected = currentTheme == AppTheme.MATERIAL3_EXPRESSIVE,
                    shape = RoundedCornerShape(32.dp),
                    background = Color(0xFF272330),
                    border = BorderStroke(1.dp, Color(0xFF4E465A).copy(alpha = 0.7f)),
                    accentColor = Color(0xFFD0BCFF),
                    tagText = "Google Pixel M3 • Dynamic Tonal Elevation",
                    onSelect = { ThemeManager.setTheme(AppTheme.MATERIAL3_EXPRESSIVE) }
                )

                // 3. MIUI Classic Card Preview
                ThemePreviewCard(
                    theme = AppTheme.MIUI,
                    isSelected = currentTheme == AppTheme.MIUI,
                    shape = RoundedCornerShape(20.dp),
                    background = Color(0xFF1B1D26),
                    border = BorderStroke(1.dp, Color(0xFF34384B)),
                    accentColor = Color(0xFFFF6700),
                    tagText = "Xiaomi MIUI • Energetic Orange & Elevated Cards",
                    onSelect = { ThemeManager.setTheme(AppTheme.MIUI) }
                )

                // 4. HyperOS Sleek Card Preview
                ThemePreviewCard(
                    theme = AppTheme.HYPEROS,
                    isSelected = currentTheme == AppTheme.HYPEROS,
                    shape = RoundedCornerShape(12.dp),
                    background = Color(0xFF070B13),
                    border = BorderStroke(
                        1.4.dp,
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF00E5FF).copy(alpha = 0.90f),
                                Color(0xFF7C4DFF).copy(alpha = 0.65f),
                                Color(0xFF00E5FF).copy(alpha = 0.35f)
                            )
                        )
                    ),
                    accentColor = Color(0xFF00E5FF),
                    tagText = "Cyberpunk Void • Glowing Neon Cyan Outlines",
                    onSelect = { ThemeManager.setTheme(AppTheme.HYPEROS) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2: Custom Background & Glassmorphism
            SectionHeader(
                title = "Backdrop & Glassmorphism",
                subtitle = "Tune wallpaper transparency, frosted blur, and solid backdrops"
            )

            PoxCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Background Style",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BackgroundType.values().forEach { type ->
                            val isSelected = bgConfig.type == type
                            FilterChip(
                                selected = isSelected,
                                onClick = { ThemeManager.setBackgroundType(type) },
                                label = { Text(type.displayName) },
                                shape = tokens.pillShape,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Solid color choices
                    AnimatedVisibility(visible = bgConfig.type == BackgroundType.SOLID_COLOR) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Color Palette",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val solidColors = listOf(
                                0xFF0F172AL to "Obsidian",
                                0xFF0B132BL to "Midnight",
                                0xFF000000L to "AMOLED",
                                0xFF18181BL to "Zinc",
                                0xFF1E1035L to "Deep Violet",
                                0xFF062826L to "Emerald Void"
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                solidColors.forEach { (colorHex, _) ->
                                    val isColorSelected = bgConfig.colorHex == colorHex
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(Color(colorHex))
                                            .border(
                                                width = if (isColorSelected) 3.dp else 1.dp,
                                                color = if (isColorSelected) tokens.themeAccent else Color.Gray.copy(alpha = 0.5f),
                                                shape = CircleShape
                                            )
                                            .clickable { ThemeManager.setBackgroundColor(colorHex) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isColorSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Custom image launcher
                    AnimatedVisibility(visible = bgConfig.type == BackgroundType.CUSTOM_IMAGE) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = tokens.pillShape
                            ) {
                                Icon(Icons.Default.Wallpaper, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose Wallpaper from Gallery")
                            }
                            if (bgConfig.imageUri != null) {
                                TextButton(
                                    onClick = { ThemeManager.setBackgroundImageUri(null) },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Reset Image", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // Opacity Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Glass Surface Opacity",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${(bgConfig.opacity * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = tokens.themeAccent
                            )
                        }
                        Slider(
                            value = bgConfig.opacity,
                            onValueChange = { ThemeManager.setBackgroundOpacity(it) },
                            valueRange = 0.20f..1.0f
                        )
                    }

                    // Blur Radius Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Backdrop Frosted Blur",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${bgConfig.blurRadiusDp.toInt()} dp",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = tokens.themeAccent
                            )
                        }
                        Slider(
                            value = bgConfig.blurRadiusDp,
                            onValueChange = { ThemeManager.setBackgroundBlur(it) },
                            valueRange = 0f..50f
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 3: Adaptive Silicon & Battery Learning Engine
            SectionHeader(
                title = "Adaptive Silicon & Battery Learning",
                subtitle = "Self-calibrating battery runtime and thermal degradation model"
            )

            PoxCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Adaptive Calibration",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Automatically tunes voltage margins and charging wattages based on battery wear and thermal stress.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = learningStats.isAutoEnabled,
                            onCheckedChange = { enabled ->
                                AdaptiveLearningManager.setAutoEnabled(enabled)
                                learningStats = AdaptiveLearningManager.getStats()
                            }
                        )
                    }

                    HorizontalDivider()

                    // Stats Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = tokens.pillShape,
                            color = tokens.badgeBackground
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Completed Cycles", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${learningStats.totalCycles}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = tokens.pillShape,
                            color = tokens.badgeBackground
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Efficiency Index", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${learningStats.efficiencyIndex}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tokens.themeAccent)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = tokens.pillShape,
                            color = tokens.badgeBackground
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Mean Load Temp", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${String.format("%.1f", learningStats.averageLoadTempC)}°C", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = tokens.pillShape,
                            color = tokens.badgeBackground
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Last Calibrated", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(learningStats.lastCalibratedTimestamp, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isCalibrating = true
                                val tier = TuningRepository.getActiveEasyTier()
                                val profile = TuningRepository.getActiveEasyProfile()
                                learningStats = AdaptiveLearningManager.runCalibration(tier, profile)
                                isCalibrating = false
                                snackbarHostState.showSnackbar("Adaptive calibration cycle completed successfully")
                            }
                        },
                        enabled = !isCalibrating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = tokens.pillShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.themeAccent
                        )
                    ) {
                        if (isCalibrating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Calibrating Silicon...")
                        } else {
                            Icon(Icons.Default.Psychology, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Run Calibration Cycle Now")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 4: Quick Settings Tiles Guide
            SectionHeader(
                title = "Quick Settings (QS) Tiles",
                subtitle = "Pox Kernel tiles available for your system status bar"
            )

            PoxCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    QSTileRow(
                        title = "BegoTorch Overdrive",
                        description = "Instant 150mA flashlight boost via MediaTek perfmgr torch brightness node",
                        icon = Icons.Default.FlashlightOn
                    )
                    QSTileRow(
                        title = "Pox Mode Cycler",
                        description = "Cycle directly between Easy Mode performance tiers from the notification shade",
                        icon = Icons.Default.Tune
                    )
                    QSTileRow(
                        title = "Bypass Charging",
                        description = "Direct motherboard power bypass toggle to prevent battery heat during gaming",
                        icon = Icons.Default.PowerSettingsNew
                    )
                    QSTileRow(
                        title = "Fast Charge Wattage",
                        description = "Hardware wattage lock switch between standard and peak dual-switch current",
                        icon = Icons.Default.BatteryChargingFull
                    )

                    Surface(
                        shape = tokens.pillShape,
                        color = tokens.badgeBackground
                    ) {
                        Text(
                            text = "Tip: Swipe down your notification bar twice, tap the pencil edit icon, and drag the Pox Kernel tiles into your quick panel.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 5: Diagnostic & Maintenance Shortcuts
            SectionHeader(
                title = "Maintenance & Logs",
                subtitle = "Firmware updates, kernel dmesg inspection and system health"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PoxCard(
                    onClick = onNavigateToUpdater,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = tokens.themeAccent)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Kernel OTA Updates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Check for newer GitHub releases and flashable AnyKernel3 packages", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }

                PoxCard(
                    onClick = onNavigateToDiagnostics,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null, tint = tokens.themeAccent)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Diagnostics & Live Telemetry", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Inspect kernel ring buffer (dmesg), SELinux status, and sysfs nodes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(
    theme: AppTheme,
    isSelected: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    background: Color,
    border: BorderStroke?,
    accentColor: Color,
    tagText: String,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = background),
        border = if (isSelected) BorderStroke(2.dp, accentColor) else border,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Active",
                                    tint = if (accentColor == Color(0xFFD0BCFF) || accentColor == Color(0xFF00E5FF)) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Column {
                        Text(
                            text = theme.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = tagText,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor
                        )
                    }
                }

                if (isSelected) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accentColor.copy(alpha = 0.25f)
                    ) {
                        Text(
                            text = "ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = theme.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QSTileRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val tokens = LocalThemeTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = tokens.badgeBackground,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tokens.themeAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
