package com.pox.kernelmanager.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pox.kernelmanager.data.model.HardwareStats

@Composable
fun ChargingDetailCard(
    stats: HardwareStats,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    com.pox.kernelmanager.ui.theme.PoxCard(
        onClick = onClick,
        enabled = onClick != null,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header: Battery & Power Status Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = if (stats.isBypassActive) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else if (stats.isCharging) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    stats.isBypassActive -> Icons.Default.ElectricBolt
                                    stats.isCharging -> Icons.Default.BatteryChargingFull
                                    else -> Icons.Default.BatteryStd
                                },
                                contentDescription = null,
                                tint = when {
                                    stats.isBypassActive -> MaterialTheme.colorScheme.onTertiaryContainer
                                    stats.isCharging -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Battery & Charging Subsystem",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stats.batteryStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Power Flow Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        stats.isBypassActive -> MaterialTheme.colorScheme.tertiaryContainer
                        stats.batteryCurrentMa > 0 -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ) {
                    val sign = if (stats.batteryCurrentMa > 0) "+" else ""
                    Text(
                        text = if (stats.isBypassActive) "BYPASS" else "$sign${String.format("%.1f", stats.batteryWattageW)} W",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            stats.isBypassActive -> MaterialTheme.colorScheme.onTertiaryContainer
                            stats.batteryCurrentMa > 0 -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Large Gauge & Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${stats.batteryLevel}",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Source: ${stats.chargerSource}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (stats.chargeLimitPercent < 100) {
                        Text(
                            text = "Stop Limit: ${stats.chargeLimitPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Charge Level Progress Indicator
            LinearProgressIndicator(
                progress = { (stats.batteryLevel / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = if (stats.isBypassActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 4-Tile Telemetry Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ChargingStatTile(
                    title = "Current",
                    value = if (stats.isBypassActive) "0 mA" else "${stats.batteryCurrentMa} mA",
                    icon = Icons.Default.Speed,
                    modifier = Modifier.weight(1f)
                )
                ChargingStatTile(
                    title = "Voltage",
                    value = "${stats.batteryVoltageMv} mV",
                    icon = Icons.Default.ElectricMeter,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val tempColor = when {
                    stats.batteryTempC >= 45.0f -> MaterialTheme.colorScheme.error
                    stats.batteryTempC >= 38.0f -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
                ChargingStatTile(
                    title = "Battery Temp",
                    value = "${String.format("%.1f", stats.batteryTempC)}°C",
                    icon = Icons.Default.Thermostat,
                    valueColor = tempColor,
                    modifier = Modifier.weight(1f)
                )
                ChargingStatTile(
                    title = "Health / Tech",
                    value = "${stats.batteryHealth} • ${stats.batteryTechnology}",
                    icon = Icons.Default.HealthAndSafety,
                    modifier = Modifier.weight(1f)
                )
            }

            if (stats.designCapacityMah > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Design Capacity: ${stats.designCapacityMah} mAh",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "Standard SDP: 500mA • Fast: 3000mA",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChargingStatTile(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = valueColor
                )
            }
        }
    }
}
