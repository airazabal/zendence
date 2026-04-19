package com.example.zendence.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.zendence.MeditationViewModel

@Composable
fun MeditationTimerScreen(viewModel: MeditationViewModel) {
    // Note: MeditationViewModel uses timeLeftSec (Int) instead of timeLeft (Flow/State)
    // and doesn't have a toggleTimer() method yet, so we adapt to its current API.
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Time Remaining", style = MaterialTheme.typography.labelMedium)

        // Large Countdown Display
        Text(
            text = String.format("%02d:%02d", viewModel.timeLeftSec / 60, viewModel.timeLeftSec % 60),
            style = MaterialTheme.typography.displayLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = { 
            if (viewModel.isRunning) {
                viewModel.isRunning = false 
            } else {
                viewModel.startTimer(scope)
            }
        }) {
            Text(if (viewModel.isRunning) "Pause" else "Start")
        }
    }
}