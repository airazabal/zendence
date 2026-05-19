package com.alex.zendence.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.zendence.MeditationViewModel

@Composable
fun AiTrendsSection(vm: MeditationViewModel) {
    var isExpanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Insights & Trends", color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold)
                }
                Icon(if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    val isAnalyzing = vm.aiAnalysis.contains("Gathering") || 
                                     vm.aiAnalysis.contains("Analyzing") || 
                                     vm.aiAnalysis.contains("Thinking")

                    if (isAnalyzing) {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = vm.aiAnalysis,
                            style = TextStyle(
                                fontSize = 14.sp,
                                color = if (vm.aiAnalysis.startsWith("❌") || vm.aiAnalysis.startsWith("❗")) 
                                    MaterialTheme.colorScheme.error 
                                else MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 500.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(end = 40.dp) // Space for copy button
                        )

                        val showCopy = vm.aiAnalysis.isNotBlank() && 
                                      !isAnalyzing && 
                                      vm.aiAnalysis != "Tap to analyze your trends..."

                        if (showCopy) {
                            IconButton(
                                onClick = { clipboardManager.setText(AnnotatedString(vm.aiAnalysis)) },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(
                                    Icons.Rounded.ContentCopy, 
                                    contentDescription = "Copy Results",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { vm.performAiAnalysis() },
                        enabled = !isAnalyzing,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isAnalyzing) {
                            Text("Processing...")
                        } else {
                            Text("Analyze My Patterns")
                        }
                    }
                }
            }
        }
    }
}
