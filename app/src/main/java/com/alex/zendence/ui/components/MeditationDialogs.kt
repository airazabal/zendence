package com.alex.zendence.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
    onConfirm: (Int, Int, String, Float, String?) -> Unit
) {
    var bellAtMin by remember { mutableStateOf(editingBell?.let { (it.atSecFromStart / 60).toString() } ?: "") }
    var bellAtSec by remember { mutableStateOf(editingBell?.let { (it.atSecFromStart % 60).toString() } ?: "") }
    var repeats by remember { mutableStateOf(editingBell?.repeats?.toString() ?: "1") }
    var bellVol by remember { mutableFloatStateOf(editingBell?.volume ?: 0.7f) }
    var soundUri by remember { mutableStateOf(editingBell?.soundUri ?: "") }

    val context = LocalContext.current
    val bellPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                soundUri = it.toString()
            }
        }
    )

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
                    Text("Custom Sound (Optional URL/URI)", fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = soundUri,
                            onValueChange = { soundUri = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Leave empty for default") },
                            textStyle = TextStyle(fontSize = 11.sp)
                        )
                        IconButton(onClick = { bellPickerLauncher.launch(arrayOf("audio/*")) }) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = "Pick local file")
                        }
                    }
                }

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
                onConfirm(totalSec, repeatsInt, "custom", bellVol, soundUri.ifBlank { null })
            }) { Text(if (editingBell != null) "Update" else "Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Back") }
        }
    )
}

@Composable
fun SoundSourceDialog(
    title: String,
    initialUri: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var uri by remember { mutableStateOf(initialUri) }
    val context = LocalContext.current
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { selectedUri ->
            selectedUri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Ignore if permission can't be persisted (e.g. not a local URI)
                }
                uri = it.toString()
            }
        }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter a web URL or select a local audio file.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uri,
                        onValueChange = { uri = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("URL / URI") },
                        textStyle = TextStyle(fontSize = 12.sp),
                        singleLine = true
                    )
                    IconButton(onClick = { pickerLauncher.launch(arrayOf("audio/*")) }) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = "Pick local file")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(uri) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Back") }
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
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Back") }
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
    readingText: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(readingText) }
    
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 60.dp)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { 
                        if (isEditing) {
                            onSave(editText)
                            isEditing = false
                        } else {
                            isEditing = true
                        }
                    }) {
                        Icon(
                            if (isEditing) Icons.Rounded.Check else Icons.Rounded.Edit,
                            contentDescription = if (isEditing) "Save" else "Edit",
                            tint = Color(0xFFFF69B4)
                        )
                    }

                    Icon(
                        Icons.Rounded.FormatQuote,
                        contentDescription = null,
                        tint = Color(0xFFFF69B4),
                        modifier = Modifier.size(48.dp)
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (isEditing) {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF69B4),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            cursorColor = Color(0xFFFF69B4)
                        )
                    )
                } else {
                    Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text(
                            text = readingText,
                            style = TextStyle(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Light,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                lineHeight = 30.sp
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                TextButton(onClick = onDismiss) {
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
}
