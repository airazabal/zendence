package com.alex.zendence.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.zendence.IntervalBell
import com.alex.zendence.MeditationViewModel
import com.alex.zendence.Preset
import com.alex.zendence.R
import kotlinx.coroutines.CoroutineScope
import java.util.Date

@Composable
fun IntervalBellDialog(
    editingBell: IntervalBell?,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int, Float) -> Unit
) {
    var bellAtMin by remember { mutableStateOf(editingBell?.let { (it.atSecFromStart / 60).toString() } ?: "") }
    var bellAtSec by remember { mutableStateOf(editingBell?.let { (it.atSecFromStart % 60).toString() } ?: "") }
    var repeats by remember { mutableStateOf(editingBell?.repeats?.toString() ?: "1") }
    var bellVol by remember { mutableFloatStateOf(editingBell?.volume ?: 0.7f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingBell != null) "Edit Interval Bell" else "Add Interval Bell") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = bellAtMin, onValueChange = { bellAtMin = it }, label = { Text("Min") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = bellAtSec, onValueChange = { bellAtSec = it }, label = { Text("Sec") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                OutlinedTextField(value = repeats, onValueChange = { repeats = it }, label = { Text("Repeats") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                
                Column {
                    Text("Bell Volume: ${(bellVol * 100).toInt()}%", fontSize = 12.sp)
                    Slider(value = bellVol, onValueChange = { bellVol = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val totalSec = ((bellAtMin.toIntOrNull() ?: 0) * 60) + (bellAtSec.toIntOrNull() ?: 0)
                val repeatsInt = repeats.toIntOrNull() ?: 1
                onConfirm(totalSec, repeatsInt, 0 /* type not used here */, bellVol)
            }) { Text(if (editingBell != null) "Update" else "Add") }
        }
    )
}

@Composable
fun SavePresetDialog(
    presetToEdit: Preset?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(presetToEdit?.name ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (presetToEdit != null) "Edit Preset" else "Save Preset") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Preset Name") }, enabled = presetToEdit == null) },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    onConfirm(name)
                }
            }) { Text(if (presetToEdit != null) "Update" else "Save") }
        }
    )
}

@Composable
fun DeletePresetDialog(
    presetName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Preset") },
        text = { Text("Delete '$presetName'?") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ClearHistoryDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear History") },
        text = { Text("Delete all your meditation history? This cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Clear All", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun InsightDialog(
    vm: MeditationViewModel,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var insightText by remember(vm.editingMeditation) { mutableStateOf(vm.editingMeditation?.insight ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (vm.editingMeditation?.insight == null) "Session Complete" else "Edit Insight") },
        text = {
            Column {
                if (vm.editingMeditation?.insight == null) {
                    Text("Great job! You meditated for ${vm.lastSessionMinutes} minutes.", modifier = Modifier.padding(bottom = 12.dp))
                }
                OutlinedTextField(
                    value = insightText,
                    onValueChange = { insightText = it },
                    label = { Text("How do you feel?") },
                    placeholder = { Text("e.g. Peaceful, focused, restless...") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(insightText) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (vm.editingMeditation?.insight == null) "Skip" else "Cancel")
            }
        }
    )
}

@Composable
fun FullReadingDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 40.dp, vertical = 60.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Rounded.FormatQuote,
                    contentDescription = null,
                    tint = Color(0xFFFF69B4),
                    modifier = Modifier.size(48.dp)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                val meditationText = remember {
                    try {
                        context.resources.openRawResource(R.raw.meditation).bufferedReader().use { it.readText() }
                    } catch (ignore: Exception) {
                        ""
                    }
                }
                
                Text(
                    text = meditationText,
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 30.sp
                    )
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                Text(
                    "CLOSE",
                    style = TextStyle(
                        color = Color(0xFFFF69B4),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontSize = 14.sp
                    )
                )
            }
        }
    }
}
