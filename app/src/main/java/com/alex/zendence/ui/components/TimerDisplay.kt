package com.alex.zendence.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.zendence.MeditationViewModel
import java.util.*

@Composable
fun TimerDisplay(
    vm: MeditationViewModel,
    isEditingDuration: Boolean,
    editValue: String,
    onEditValueChange: (String) -> Unit,
    onEditDurationToggle: (Boolean) -> Unit,
    onShowFullReading: () -> Unit
) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
        CircularProgressIndicator(
            progress = { if (vm.initialDurationSec > 0) vm.timeLeftSec.toFloat() / vm.initialDurationSec else 0f },
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!vm.isRunning) {
                    IconButton(onClick = { vm.decrementDuration() }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Minus", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                if (isEditingDuration && !vm.isRunning) {
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = {
                            onEditValueChange(it)
                            val mins = it.toIntOrNull() ?: 0
                            if (mins > 0) vm.updateDuration(mins)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, fontSize = 32.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            onEditDurationToggle(false)
                        })
                    )
                } else {
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", vm.timeLeftSec / 60, vm.timeLeftSec % 60),
                        style = TextStyle(
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            shadow = Shadow(color = Color.Black, blurRadius = 8f)
                        ),
                        modifier = Modifier.clickable(enabled = !vm.isRunning) {
                            onEditValueChange((vm.initialDurationSec / 60).toString())
                            onEditDurationToggle(true)
                        }
                    )
                }

                if (!vm.isRunning) {
                    IconButton(onClick = { vm.incrementDuration() }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Plus", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Text(
                text = vm.dailyQuote,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFFFF69B4),
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(0.8f)
                    .clickable { onShowFullReading() }
            )

            Text(
                text = if (vm.isRunning) "BREATHE" else "READY",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 4f)
                )
            )
        }
    }
}
