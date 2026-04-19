package com.example.zendence


import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
    val soundType: String,
    val repeats: Int = 1
)

class MeditationViewModel(application: android.app.Application) : ViewModel() {
    private val db = Room.databaseBuilder(application, AppDatabase::class.java, "meditation-db").build()
    private val dao = db.meditationDao()
    private val notificationManager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val history = dao.getAll()
    
    private var previousInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL

    var isStartingBellPlaying by mutableStateOf(false)
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

    fun startTimer(scope: kotlinx.coroutines.CoroutineScope, onPlayBell: (String, Int) -> Unit = { _, _ -> }) {
        val isFirstStart = timeLeftSec == initialDurationSec
        isRunning = true
        
        // Try to set Do Not Disturb
        if (notificationManager.isNotificationPolicyAccessGranted) {
            previousInterruptionFilter = notificationManager.currentInterruptionFilter
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        }

        scope.launch {
            if (startingBellEnabled && isFirstStart) {
                isStartingBellPlaying = true
                onPlayBell("starting_bell", 1)
            }

            // Snapshot the bells to avoid concurrent modification during iteration
            val snapshotBells = intervalBells.toList()

            while (timeLeftSec > 0 && isRunning) {
                delay(1000)
                timeLeftSec--

                val elapsedSec = initialDurationSec - timeLeftSec
                snapshotBells.forEach { bell ->
                    if (elapsedSec == bell.atSecFromStart) {
                        onPlayBell(bell.soundType, bell.repeats)
                    }
                }
            }
            if (timeLeftSec <= 0 && isRunning) { // Only play end bell if it wasn't manually stopped
                onPlayBell("starting_bell", 1) // End bell
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
        isStartingBellPlaying = false
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

    val bellPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        vm.isStartingBellPlaying = false
                    }
                }
            })
        }
    }

    fun playBell(type: String, repeats: Int = 1) {
        val resId = when (type) {
            "starting_bell" -> R.raw.starting_bell
            "interval_bell" -> R.raw.interval_bell
            else -> R.raw.starting_bell // Fallback
        }
        val mediaItem = MediaItem.fromUri("android.resource://${context.packageName}/$resId")
        bellPlayer.stop()
        bellPlayer.clearMediaItems()
        repeat(repeats) {
            bellPlayer.addMediaItem(mediaItem)
        }
        bellPlayer.prepare()
        bellPlayer.play()
    }

    LaunchedEffect(vm.volume) {
        exoPlayer.volume = vm.volume
        bellPlayer.volume = vm.volume
    }

    LaunchedEffect(vm.isRunning, vm.isStartingBellPlaying) {
        if (vm.isRunning && !vm.isStartingBellPlaying) {
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

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.zendence_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Zendence Timer", color = Color.White) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    actions = {
                        IconButton(onClick = { activity?.finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Exit", tint = Color.White)
                        }
                    }
                )
            }
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
                            Text("-", style = MaterialTheme.typography.displayMedium, color = Color.White)
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
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White
                            ),
                            keyboardActions = KeyboardActions(onDone = {
                                val mins = editValue.toIntOrNull() ?: 0
                                if (mins > 0) vm.updateDuration(mins)
                                isEditing = false
                            })
                        )
                    } else {
                        Text(
                            text = String.format(Locale.getDefault(), "%02d:%02d", vm.timeLeftSec / 60, vm.timeLeftSec % 60),
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White,
                            modifier = Modifier.clickable(enabled = !vm.isRunning) {
                                editValue = (vm.initialDurationSec / 60).toString()
                                isEditing = true
                            }
                        )
                    }

                    if (!vm.isRunning) {
                        IconButton(onClick = { vm.incrementDuration() }) {
                            Text("+", style = MaterialTheme.typography.displayMedium, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Music Selection
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sound: ", color = Color.White)
                    Text(vm.selectedMusic, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Volume Control
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Volume: ", color = Color.White)
                    Slider(
                        value = vm.volume,
                        onValueChange = { vm.volume = it },
                        modifier = Modifier.fillMaxWidth(0.8f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.5f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bell Settings
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Starting/Ending Bell", color = Color.White)
                            Spacer(modifier = Modifier.weight(1f))
                            Switch(checked = vm.startingBellEnabled, onCheckedChange = { vm.startingBellEnabled = it })
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.3f))
                        Text("Interval Bells", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        
                        vm.intervalBells.forEach { bell ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                val repeatSuffix = if (bell.repeats > 1) " (x${bell.repeats})" else ""
                                Text("${bell.atSecFromStart / 60}m ${bell.atSecFromStart % 60}s from start (${bell.soundType})$repeatSuffix", color = Color.White)
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = { vm.intervalBells.remove(bell) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                                }
                            }
                        }
                        
                        Button(
                            onClick = { showIntervalDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !vm.isRunning,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.3f), contentColor = Color.White)
                        ) {
                            Text("Add Interval Bell")
                        }
                    }
                }

                if (showIntervalDialog) {
                    IntervalBellDialog(
                        onDismiss = { showIntervalDialog = false },
                        onConfirm = { atSecFromStart, type, repeats ->
                            vm.intervalBells.add(IntervalBell(atSecFromStart = atSecFromStart, soundType = type, repeats = repeats))
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
                                vm.startTimer(scope) { type, repeats -> playBell(type, repeats) }
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp), color = Color.White.copy(alpha = 0.3f))

                // History List
                Text("Past Meditations", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                LazyColumn {
                    items(history) { session ->
                        val date = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(session.timestamp))
                        ListItem(
                            headlineContent = { Text("${session.durationMinutes} Minute Session", color = Color.White) },
                            supportingContent = { Text(date, color = Color.White.copy(alpha = 0.7f)) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IntervalBellDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int, String, Int) -> Unit,
    maxSec: Int
) {
    var timeMin by remember { mutableStateOf("1") }
    var timeSec by remember { mutableStateOf("0") }
    var repeats by remember { mutableStateOf("1") }
    var soundType by remember { mutableStateOf("interval_bell") }
    val bellTypes = listOf("interval_bell", "starting_bell")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Interval Bell") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = timeMin,
                        onValueChange = { timeMin = it },
                        label = { Text("Min") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = timeSec,
                        onValueChange = { timeSec = it },
                        label = { Text("Sec") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = repeats,
                    onValueChange = { repeats = it },
                    label = { Text("Number of rings") },
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
                val totalSec = (timeMin.toIntOrNull() ?: 0) * 60 + (timeSec.toIntOrNull() ?: 0)
                val repeatCount = repeats.toIntOrNull() ?: 1
                if (totalSec in 1 until maxSec) {
                    onConfirm(totalSec, soundType, repeatCount)
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