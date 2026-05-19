package com.alex.zendence.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsAndHistoryHeader(
    showSettings: Boolean,
    onSettingsToggle: () -> Unit,
    showHistory: Boolean,
    onHistoryToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "SETTINGS",
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.tertiary,
                shadow = Shadow(color = Color.Black.copy(alpha = 0.8f), blurRadius = 8f)
            )
        )
        Row {
            IconButton(onClick = onSettingsToggle) {
                Icon(if (showSettings) Icons.Rounded.ExpandLess else Icons.Rounded.Settings, contentDescription = "Toggle Settings", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onHistoryToggle) {
                Icon(if (showHistory) Icons.Rounded.ExpandLess else Icons.Rounded.History, contentDescription = "Toggle History", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
