package com.example.zendence


import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.text.input.KeyboardType
import androidx.room.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// --- 1. DATA LAYER (ROOM) ---
@Entity(tableName = "meditations")
data class Meditation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val durationMinutes: Int
)

@Dao
interface MeditationDao {
    @Query("SELECT * FROM meditations ORDER BY timestamp DESC")
    fun getAll(): Flow<List<Meditation>>
    @Insert
    suspend fun insert(meditation: Meditation)
}

@Database(entities = [Meditation::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun meditationDao(): MeditationDao
}

// --- 2. VIEWMODEL ---
data class IntervalBell(
    val id: String = UUID.randomUUID().toString(),
    val atSecFromStart: Int, // Changed to be from start
    val soundType: String
)

class MeditationViewModel(application: android.app.Application) : ViewModel() {
    private val db = Room.databaseBuilder(application, AppDatabase::class.java, "meditation-db").build()
    private val dao = db.meditationDao()
    private val notificationManager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val history = dao.getAll()
    
    private var previousInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL

    var isRunning by mutableStateOf(false)
    var initialDurationSec by mutableIntStateOf(600)
    var timeLeftSec by mutableIntStateOf(600) // Default 10 mins
    var selectedMusic by mutableStateOf("Nature Stream")
    var volume by mutableFloatStateOf(1.0f)

    var startingBellEnabled by mutableStateOf(true)
    val intervalBells = mutableStateListOf<IntervalBell>()

    fun incrementDuration() {
        if (!isRunning) {
            initialDurationSec += 60
            timeLeftSec = initialDurationSec
        }
    }

    fun decrementDuration() {
        if (!isRunning && initialDurationSec > 60) {
            initialDurationSec -= 60
            timeLeftSec = initialDurationSec
        }
    }

    fun updateDuration(minutes: Int) {
        if (!isRunning && minutes > 0) {
            initialDurationSec = minutes * 60
            timeLeftSec = initialDurationSec
        }
    }

    fun startTimer(scope: kotlinx.coroutines.CoroutineScope, onPlayBell: (String) -> Unit = {}) {
        val isFirstStart = timeLeftSec == initialDurationSec
        isRunning = true
        
        // Try to set Do Not Disturb
        if (notificationManager.isNotificationPolicyAccessGranted) {
            previousInterruptionFilter = notificationManager.currentInterruptionFilter
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        }

        scope.launch {
            if (startingBellEnabled && isFirstStart) {
                onPlayBell("starting_bell")
            }

            // Snapshot the bells to avoid concurrent modification during iteration
            val snapshotBells = intervalBells.toList()

            while (timeLeftSec > 0 && isRunning) {
                delay(1000)
                timeLeftSec--

                val elapsedSec = initialDurationSec - timeLeftSec
                snapshotBells.forEach { bell ->
                    if (elapsedSec == bell.atSecFromStart) {
                        onPlayBell(bell.soundType)
                    }
                }
            }
            if (timeLeftSec <= 0 && isRunning) { // Only play end bell if it wasn't manually stopped
                onPlayBell("starting_bell") // End bell
                saveSession(initialDurationSec / 60)
                stopAndResetDND()
            }
        }
    }

    fun stopTimer(scope: kotlinx.coroutines.CoroutineScope) {
        val elapsedMinutes = (initialDurationSec - timeLeftSec) / 60
        if (timeLeftSec > 0 && elapsedMinutes > 0) {
            scope.launch {
                saveSession(elapsedMinutes)
            }
        }
        stopAndResetDND()
        timeLeftSec = initialDurationSec
    }
    
    private fun stopAndResetDND() {
        isRunning = false
        if (notificationManager.isNotificationPolicyAccessGranted) {
            notificationManager.setInterruptionFilter(previousInterruptionFilter)
        }
    }

    private suspend fun saveSession(minutes: Int) {
        dao.insert(Meditation(timestamp = System.currentTimeMillis(), durationMinutes = minutes))
    }
}

// --- 3. UI COMPONENTS ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeditationApp(vm: MeditationViewModel) {
    val context = LocalContext.current
    val history by vm.history.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showIntervalDialog by remember { mutableStateOf(false) }
    
    val notificationManager = remember { context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    // Handle Keep Screen On
    val activity = context as? ComponentActivity
    SideEffect {
        if (vm.isRunning) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Audio Player setup
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri("android.resource://${context.packageName}/raw/nature_stream")
            setMediaItem(mediaItem)
            repeatMode = ExoPlayer.REPEAT_MODE_ALL
            prepare()
        }
    }

    val bellPlayer = remember { ExoPlayer.Builder(context).build() }

    fun playBell(type: String) {
        val resId = when (type) {
            "starting_bell" -> R.raw.starting_bell
            "interval_bell" -> R.raw.interval_bell
            else -> R.raw.starting_bell // Fallback
        }
        val mediaItem = MediaItem.fromUri("android.resource://${context.packageName}/$resId")
        bellPlayer.setMediaItem(mediaItem)
        bellPlayer.prepare()
        bellPlayer.play()
    }

    LaunchedEffect(vm.volume) {
        exoPlayer.volume = vm.volume
        bellPlayer.volume = vm.volume
    }

    LaunchedEffect(vm.isRunning) {
        if (vm.isRunning) {
            if (!exoPlayer.isPlaying) exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    // Handle Stop/Reset
    LaunchedEffect(vm.timeLeftSec) {
        if (!vm.isRunning && vm.timeLeftSec == vm.initialDurationSec) {
            exoPlayer.seekTo(0)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            bellPlayer.release()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Zendence Timer") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Timer Display with +/- and Direct Entry
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!vm.isRunning) {
                    IconButton(onClick = { vm.decrementDuration() }) {
                        Text("-", style = MaterialTheme.typography.displayMedium)
                    }
                }

                var isEditing by remember { mutableStateOf(false) }
                var editValue by remember { mutableStateOf("") }

                if (isEditing && !vm.isRunning) {
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { editValue = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                        keyboardActions = KeyboardActions(onDone = {
                            val mins = editValue.toIntOrNull() ?: 0
                            if (mins > 0) vm.updateDuration(mins)
                            isEditing = false
                        })
                    )
                } else {
                    Text(
                        text = String.format("%02d:%02d", vm.timeLeftSec / 60, vm.timeLeftSec % 60),
                        style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier.clickable(enabled = !vm.isRunning) {
                            editValue = (vm.initialDurationSec / 60).toString()
                            isEditing = true
                        }
                    )
                }

                if (!vm.isRunning) {
                    IconButton(onClick = { vm.incrementDuration() }) {
                        Text("+", style = MaterialTheme.typography.displayMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Music Selection
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sound: ")
                Text(vm.selectedMusic, style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Volume Control
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Volume: ")
                Slider(
                    value = vm.volume,
                    onValueChange = { vm.volume = it },
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bell Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Starting/Ending Bell")
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(checked = vm.startingBellEnabled, onCheckedChange = { vm.startingBellEnabled = it })
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Interval Bells", style = MaterialTheme.typography.titleMedium)
                    
                    vm.intervalBells.forEach { bell ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("${bell.atSecFromStart / 60}m ${bell.atSecFromStart % 60}s from start (${bell.soundType})")
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.intervalBells.remove(bell) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                    
                    Button(
                        onClick = { showIntervalDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !vm.isRunning
                    ) {
                        Text("Add Interval Bell")
                    }
                }
            }

            if (showIntervalDialog) {
                IntervalBellDialog(
                    onDismiss = { showIntervalDialog = false },
                    onConfirm = { atSecFromStart, type ->
                        vm.intervalBells.add(IntervalBell(atSecFromStart = atSecFromStart, soundType = type))
                        showIntervalDialog = false
                    },
                    maxSec = vm.initialDurationSec
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Controls
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text("Enable 'Do Not Disturb' Permission")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (vm.isRunning) {
                            vm.isRunning = false
                        } else {
                            vm.startTimer(scope) { type -> playBell(type) }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (vm.isRunning) "Pause" else "Start Meditation")
                }

                if (!vm.isRunning && vm.timeLeftSec < vm.initialDurationSec) {
                    OutlinedButton(
                        onClick = { vm.stopTimer(scope) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (vm.timeLeftSec == 0) "Reset" else "Stop")
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 24.dp))

            // History List
            Text("Past Meditations", style = MaterialTheme.typography.headlineSmall)
            LazyColumn {
                items(history) { session ->
                    val date = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(session.timestamp))
                    ListItem(
                        headlineContent = { Text("${session.durationMinutes} Minute Session") },
                        supportingContent = { Text(date) }
                    )
                }
            }
        }
    }
}

@Composable
fun IntervalBellDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit,
    maxSec: Int
) {
    var timeMin by remember { mutableStateOf("1") }
    var soundType by remember { mutableStateOf("interval_bell") }
    val bellTypes = listOf("interval_bell", "starting_bell")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Interval Bell") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = timeMin,
                    onValueChange = { timeMin = it },
                    label = { Text("Minutes from start") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Sound Type", style = MaterialTheme.typography.labelLarge)
                bellTypes.forEach { type ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { soundType = type }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(selected = (soundType == type), onClick = { soundType = type })
                        Text(type, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val sec = (timeMin.toIntOrNull() ?: 0) * 60
                if (sec > 0 && sec < maxSec) {
                    onConfirm(sec, soundType)
                }
            }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// --- 4. MAIN ACTIVITY ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MeditationViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MeditationViewModel(application) as T
                }
            })
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MeditationApp(vm)
                }
            }
        }
    }
}