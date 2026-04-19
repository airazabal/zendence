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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val atSecFromStart: Int,
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
    var initialDurationSec by mutableIntStateOf(2700) // Default 45 mins
    var timeLeftSec by mutableIntStateOf(2700)
    var selectedMusic by mutableStateOf("Nature Stream")
    var volume by mutableFloatStateOf(0.5f)

    var startingBellEnabled by mutableStateOf(true)
    val intervalBells = mutableStateListOf<IntervalBell>(
        IntervalBell(atSecFromStart = 900, soundType = "interval_bell", repeats = 1),
        IntervalBell(atSecFromStart = 1800, soundType = "interval_bell", repeats = 2),
        IntervalBell(atSecFromStart = 2699, soundType = "interval_bell", repeats = 3)
    )

    fun toggleTimer(scope: kotlinx.coroutines.CoroutineScope, onPlayBell: (String, Int) -> Unit) {
        if (isRunning) {
            isRunning = false
        } else {
            startTimer(scope, onPlayBell)
        }
    }

    fun resetToDefaults() {
        if (!isRunning) {
            initialDurationSec = 2700
            timeLeftSec = 2700
            intervalBells.clear()
            intervalBells.addAll(
                listOf(
                    IntervalBell(atSecFromStart = 900, soundType = "interval_bell", repeats = 1),
                    IntervalBell(atSecFromStart = 1800, soundType = "interval_bell", repeats = 2),
                    IntervalBell(atSecFromStart = 2699, soundType = "interval_bell", repeats = 3)
                )
            )
        }
    }

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
        if (timeLeftSec <= 0) {
            timeLeftSec = initialDurationSec
        }
        val isFirstStart = timeLeftSec == initialDurationSec
        isRunning = true
        
        if (notificationManager.isNotificationPolicyAccessGranted) {
            previousInterruptionFilter = notificationManager.currentInterruptionFilter
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        }

        scope.launch {
            if (startingBellEnabled && isFirstStart) {
                isStartingBellPlaying = true
                onPlayBell("starting_bell", 1)
            }

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
            if (timeLeftSec <= 0 && isRunning) {
                onPlayBell("starting_bell", 1)
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
    
    var isEditingDuration by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf("") }
    
    val notificationManager = remember { context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    val focusRequester = remember { FocusRequester() }

    val activity = context as? ComponentActivity
    SideEffect {
        if (vm.isRunning) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

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
            else -> R.raw.starting_bell
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

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    if (showIntervalDialog) {
        IntervalBellDialog(
            onDismiss = { showIntervalDialog = false },
            onConfirm = { time, sound, repeats ->
                vm.intervalBells.add(IntervalBell(atSecFromStart = time, soundType = sound, repeats = repeats))
                showIntervalDialog = false
            },
            maxSec = vm.initialDurationSec
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                if (!isEditingDuration && keyEvent.key == Key.Spacebar) {
                    if (keyEvent.type == KeyEventType.KeyUp) {
                        vm.toggleTimer(scope) { type, repeats -> playBell(type, repeats) }
                    }
                    true
                } else {
                    false
                }
            }
            .focusRequester(focusRequester)
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusRequester.requestFocus()
            }
    ) {
        Image(
            painter = painterResource(id = R.drawable.zendence_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "ZENDENCE",
                            style = TextStyle(
                                fontWeight = FontWeight.Light,
                                letterSpacing = 4.sp,
                                color = Color.White,
                                shadow = Shadow(color = Color.Black, blurRadius = 8f)
                            )
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                    actions = {
                        IconButton(onClick = { activity?.finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Exit", tint = Color.White)
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
                        CircularProgressIndicator(
                            progress = { vm.timeLeftSec.toFloat() / vm.initialDurationSec },
                            modifier = Modifier.fillMaxSize(),
                            color = Color.White,
                            strokeWidth = 4.dp,
                            trackColor = Color.White.copy(alpha = 0.2f),
                        )
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!vm.isRunning) {
                                    IconButton(onClick = { vm.decrementDuration() }) {
                                        Icon(Icons.Rounded.KeyboardArrowLeft, contentDescription = "Minus", tint = Color.White)
                                    }
                                }

                                if (isEditingDuration && !vm.isRunning) {
                                    OutlinedTextField(
                                        value = editValue,
                                        onValueChange = { editValue = it },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.width(100.dp),
                                        singleLine = true,
                                        textStyle = TextStyle(color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 32.sp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color.White,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                                            cursorColor = Color.White
                                        ),
                                        keyboardActions = KeyboardActions(onDone = {
                                            val mins = editValue.toIntOrNull() ?: 0
                                            if (mins > 0) vm.updateDuration(mins)
                                            isEditingDuration = false
                                        })
                                    )
                                } else {
                                    Text(
                                        text = String.format(Locale.getDefault(), "%02d:%02d", vm.timeLeftSec / 60, vm.timeLeftSec % 60),
                                        style = TextStyle(
                                            fontSize = 64.sp,
                                            fontWeight = FontWeight.ExtraLight,
                                            color = Color.White,
                                            shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 12f)
                                        ),
                                        modifier = Modifier.clickable(enabled = !vm.isRunning) {
                                            editValue = (vm.initialDurationSec / 60).toString()
                                            isEditingDuration = true
                                        }
                                    )
                                }

                                if (!vm.isRunning) {
                                    IconButton(onClick = { vm.incrementDuration() }) {
                                        Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = "Plus", tint = Color.White)
                                    }
                                }
                            }
                            Text(
                                text = if (vm.isRunning) "BREATHE" else "READY",
                                style = TextStyle(
                                    fontWeight = FontWeight.Light,
                                    letterSpacing = 2.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(24.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(vm.selectedMusic, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Slider(
                                    value = vm.volume,
                                    onValueChange = { vm.volume = it },
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Starting/Ending Bell", color = Color.White, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.weight(1f))
                                Switch(
                                    checked = vm.startingBellEnabled,
                                    onCheckedChange = { vm.startingBellEnabled = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color.White.copy(alpha = 0.5f))
                                )
                            }
                            
                            if (vm.intervalBells.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.1f))
                                Text("Interval Bells", style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.6f))
                                
                                vm.intervalBells.forEach { bell ->
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        val repeatSuffix = if (bell.repeats > 1) " (x${bell.repeats})" else ""
                                        Text("${bell.atSecFromStart / 60}m ${bell.atSecFromStart % 60}s", color = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(bell.soundType.replace("_", " "), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                        Text(repeatSuffix, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                        Spacer(modifier = Modifier.weight(1f))
                                        IconButton(onClick = { vm.intervalBells.remove(bell) }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedButton(
                                onClick = { showIntervalDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !vm.isRunning,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add Interval Bell")
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { vm.toggleTimer(scope) { type, repeats -> playBell(type, repeats) } },
                            modifier = Modifier.weight(1f).height(64.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                        ) {
                            Icon(if (vm.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (vm.isRunning) "PAUSE" 
                                       else if (vm.timeLeftSec < vm.initialDurationSec) "RESUME" 
                                       else "START", 
                                fontWeight = FontWeight.Bold, 
                                letterSpacing = 1.sp
                            )
                        }

                        if (vm.isRunning || vm.timeLeftSec < vm.initialDurationSec) {
                            Button(
                                onClick = { vm.stopTimer(scope) },
                                modifier = Modifier.weight(1f).height(64.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                            ) {
                                Icon(Icons.Rounded.Stop, contentDescription = "Stop")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("STOP", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            }
                        }
                    }

                    if (!notificationManager.isNotificationPolicyAccessGranted) {
                        TextButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                context.startActivity(intent)
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Enable Do Not Disturb for silence", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "JOURNAL",
                            style = TextStyle(
                                fontWeight = FontWeight.Light,
                                letterSpacing = 2.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        )
                        Text("${history.size} sessions", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.1f))
                }

                items(history) { session ->
                    val date = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(session.timestamp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${session.durationMinutes}", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Meditation Session", color = Color.White, fontWeight = FontWeight.Medium)
                                Text(date, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                            }
                        }
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
                    @Suppress("UNCHECKED_CAST")
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
