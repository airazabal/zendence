package com.alex.zendence.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.zendence.Meditation
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistorySection(
    history: List<Meditation>,
    onExportClick: () -> Unit,
    onClearHistoryClick: () -> Unit,
    onEditInsightClick: (Meditation) -> Unit,
    onDeleteMeditationClick: (Meditation) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Sessions", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Row {
                    IconButton(onClick = onExportClick) {
                        Icon(Icons.Rounded.Share, contentDescription = "Export History", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onClearHistoryClick) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear History", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No sessions yet. Time to meditate?",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            } else {
                history.forEach { session ->
                    val date = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(session.timestamp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(date, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text("${session.durationMinutes} minutes", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                                Row {
                                    IconButton(onClick = { onEditInsightClick(session) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Insight", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { onDeleteMeditationClick(session) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                                    }
                                }
                            }
                            if (session.insight != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = session.insight,
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    ),
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
