package com.alex.zendence.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alex.zendence.MeditationViewModel

@Composable
fun MeditationTimerScreen(viewModel: MeditationViewModel) {
    // We observe the state from the ViewModel
    val seconds = viewModel.timeLeftSec
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Time Remaining", style = MaterialTheme.typography.labelMedium)

        Text(
            text = String.format("%02d:%02d", minutes, remainingSeconds),
            style = MaterialTheme.typography.displayLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = {
            viewModel.toggleTimer()
        }) {
            Text(if (viewModel.isRunning) "Stop" else "Start")
        }
    }
}