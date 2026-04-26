package com.alex.zendence.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.zendence.IntervalBell
import com.alex.zendence.MeditationViewModel
import com.alex.zendence.Preset
import kotlinx.serialization.json.Json

@Composable
fun SessionPresetsSection(
    vm: MeditationViewModel,
    presets: List<Preset>,
    onSavePresetClick: () -> Unit,
    onEditPresetClick: (Preset) -> Unit,
    onDeletePresetClick: (Preset) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "SESSIONS",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.tertiary,
                    shadow = Shadow(color = Color.Black.copy(alpha = 0.8f), blurRadius = 8f)
                )
            )
            IconButton(onClick = onSavePresetClick, enabled = !vm.isRunning) {
                Icon(Icons.Default.Save, contentDescription = "Save Preset", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        if (presets.isEmpty()) {
            Text("No saved presets", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 12.sp)
        } else {
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 200.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(presets) { preset ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.loadPreset(preset) },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    preset.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.3f), blurRadius = 4f))
                                )
                                val bellList = remember(preset.intervalBellsJson) {
                                    try {
                                        Json.decodeFromString<List<IntervalBell>>(preset.intervalBellsJson)
                                    } catch (ignore: Exception) {
                                        // Handle old format
                                        preset.intervalBellsJson.split("|").mapNotNull {
                                            val p = it.split(":")
                                            if (p.size >= 3) {
                                                IntervalBell(
                                                    atSecFromStart = p[0].toInt(),
                                                    repeats = p[1].toInt(),
                                                    soundType = p[2],
                                                    volume = if (p.size == 4) p[3].toFloatOrNull() ?: 1.0f else 1.0f
                                                )
                                            } else null
                                        }
                                    }
                                }
                                val bellCount = bellList.size
                                val bellInfo = if (bellCount > 0) {
                                    val details = bellList.joinToString(", ") {
                                        "${it.atSecFromStart}s (${(it.volume * 100).toInt()}%)"
                                    }
                                    "$bellCount bells: $details"
                                } else "no bells"
                                Text("${preset.durationMin}m • $bellInfo", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
                            }
                            IconButton(onClick = { onEditPresetClick(preset) }, enabled = !vm.isRunning) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Preset", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                            }
                            IconButton(onClick = { onDeletePresetClick(preset) }, enabled = !vm.isRunning) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }
    }
}
