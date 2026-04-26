package com.alex.zendence.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.zendence.IntervalBell
import com.alex.zendence.MeditationViewModel

@Composable
fun SettingsSection(
    vm: MeditationViewModel,
    isBellsExpanded: Boolean,
    onBellsExpandedToggle: () -> Unit,
    onAddBellClick: () -> Unit,
    onEditBellClick: (IntervalBell) -> Unit,
    onRemoveBellClick: (IntervalBell) -> Unit,
    onBackgroundSoundClick: () -> Unit,
    onStartingBellSoundClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column {
                Text("Music Volume", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Slider(
                    value = vm.volume,
                    onValueChange = {
                        vm.volume = it
                        vm.updateServiceVolume(it)
                    },
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                )
            }

            Column {
                Text("Background Sound", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    val label = if (vm.backgroundSoundUri.contains("raw/nature_stream")) "Default Stream" 
                               else vm.backgroundSoundUri.substringAfterLast("/")
                    Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                    IconButton(onClick = onBackgroundSoundClick) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit Background Sound")
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Starting Bell", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    Text("Play bell when meditation begins", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Switch(checked = vm.startingBellEnabled, onCheckedChange = { vm.startingBellEnabled = it })
            }

            if (vm.startingBellEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Starting Bell Sound", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        val label = if (vm.startingBellUri.contains("raw/starting_bell")) "Default Bell" 
                                   else vm.startingBellUri.substringAfterLast("/")
                        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                        IconButton(onClick = onStartingBellSoundClick) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit Starting Bell Sound")
                        }
                    }

                    Text("Bell Volume", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Slider(value = vm.startingBellVolume, onValueChange = { vm.startingBellVolume = it })
                }
            }

            Column {
                Text("Initial Silence: ${vm.initialSilenceSec}s", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text("Delay before music starts after starting bell", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Slider(
                    value = vm.initialSilenceSec.toFloat(),
                    onValueChange = { vm.initialSilenceSec = it.toInt() },
                    valueRange = 0f..120f,
                    steps = 24,
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.secondary, activeTrackColor = MaterialTheme.colorScheme.secondary)
                )
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onBellsExpandedToggle() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Interval Bells (${vm.intervalBells.size})", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    Icon(if (isBellsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
                }

                if (isBellsExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    vm.intervalBells.forEach { bell ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            color = Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f).clickable {
                                    onEditBellClick(bell)
                                }) {
                                    Text("${bell.atSecFromStart / 60}m ${bell.atSecFromStart % 60}s", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                                    Text("${bell.repeats}x • ${bell.soundType.replace("_", " ")} • ${(bell.volume * 100).toInt()}% vol", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                                IconButton(onClick = { onRemoveBellClick(bell) }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    Button(
                        onClick = onAddBellClick,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Interval Bell", color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }

            Button(
                onClick = { vm.resetToDefaults() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset to Defaults", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}
