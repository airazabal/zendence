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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.zendence.ui.theme.ZendenceTheme
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

@Entity(tableName = "presets")
data class Preset(
    @PrimaryKey val name: String,
    val durationMin: Int,
    val volume: Float,
    val intervalBellsJson: String // Format: "time:repeats:type|..."
)

@Dao
interface MeditationDao {
    @Query("SELECT * FROM meditations ORDER BY timestamp DESC")
    fun getAll(): Flow<List<Meditation>>
    @Insert
    suspend fun insert(meditation: Meditation)
    @Delete
    suspend fun delete(meditation: Meditation)
    @Query("DELETE FROM meditations")
    suspend fun deleteAll()

    @Query("SELECT * FROM presets ORDER BY name ASC")
    fun getAllPresets(): Flow<List<Preset>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: Preset)
    @Delete
    suspend fun deletePreset(preset: Preset)
}

@Database(entities = [Meditation::class, Preset::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun meditationDao(): MeditationDao
}

// --- 2. VIEWMODEL ---
data class IntervalBell(
    val id: String = UUID.randomUUID().toString(),
    val atSecFromStart: Int,
    val soundType: String,
    val repeats: Int = 1,
    val volume: Float = 1.0f
)

class MeditationViewModel(application: android.app.Application) : ViewModel() {
    private val db = Room.databaseBuilder(application, AppDatabase::class.java, "meditation-db")
        .fallbackToDestructiveMigration()
        .build()
    private val dao = db.meditationDao()
    private val notificationManager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val history = dao.getAll()
    val presets = dao.getAllPresets()
    
    var dailyQuote by mutableStateOf("Loading wisdom...")
    
    init {
        fetchQuote()
    }

    fun fetchQuote() {
        viewModelScope.launch {
            try {
                // Using a public API that doesn't require a key
                val response = java.net.URL("https://api.quotable.io/random?tags=spirituality|wisdom|faith").readText()
                val content = response.substringAfter("\"content\":\"").substringBefore("\"")
                val author = response.substringAfter("\"author\":\"").substringBefore("\"")
                dailyQuote = "\"$content\" — $author"
            } catch (e: Exception) {
                // Fallback to local quotes if offline
                val fallbacks = listOf(
                    "Silence is the language of God, all else is poor translation.",
                    "The soul always knows what to do to heal itself.",
                    "Quiet the mind, and the soul will speak.",
                    "Peace comes from within. Do not seek it without."
                )
                dailyQuote = fallbacks.random()
            }
        }
    }

    private var previousInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL

    var isStartingBellPlaying by mutableStateOf(false)
    var isRunning by mutableStateOf(false)
    var initialDurationSec by mutableIntStateOf(2700) // Default 45 mins
    var timeLeftSec by mutableIntStateOf(2700)
    var selectedMusic by mutableStateOf("Nature Stream")
    var volume by mutableFloatStateOf(0.5f)

    var startingBellEnabled by mutableStateOf(true)
    var startingBellVolume by mutableFloatStateOf(0.7f)
    val intervalBells = mutableStateListOf<IntervalBell>(
        IntervalBell(atSecFromStart = 900, soundType = "interval_bell", repeats = 1, volume = 0.5f),
        IntervalBell(atSecFromStart = 1800, soundType = "interval_bell", repeats = 2, volume = 0.6f),
        IntervalBell(atSecFromStart = 2699, soundType = "interval_bell", repeats = 3, volume = 0.7f)
    )

    fun toggleTimer(scope: kotlinx.coroutines.CoroutineScope, onPlayBell: (String, Int, Float) -> Unit) {
        if (isRunning) {
            isRunning = false
        } else {
            startTimer(scope, onPlayBell)
        }
    }

    fun savePreset(name: String, scope: kotlinx.coroutines.CoroutineScope) {
        val bellsJson = intervalBells
            .filter { it.atSecFromStart < initialDurationSec }
            .joinToString("|") { "${it.atSecFromStart}:${it.repeats}:${it.soundType}:${it.volume}" }
        val preset = Preset(
            name = name,
            durationMin = initialDurationSec / 60,
            volume = volume,
            intervalBellsJson = bellsJson
        )
        scope.launch {
            dao.insertPreset(preset)
        }
    }

    fun loadPreset(preset: Preset) {
        if (!isRunning) {
            initialDurationSec = preset.durationMin * 60
            timeLeftSec = initialDurationSec
            volume = preset.volume
            intervalBells.clear()
            if (preset.intervalBellsJson.isNotEmpty()) {
                preset.intervalBellsJson.split("|").forEach {
                    val parts = it.split(":")
                    if (parts.size >= 3) {
                        val vol = if (parts.size == 4) parts[3].toFloatOrNull() ?: 1.0f else 1.0f
                        intervalBells.add(
                            IntervalBell(
                                atSecFromStart = parts[0].toInt(),
                                repeats = parts[1].toInt(),
                                soundType = parts[2],
                                volume = vol
                            )
                        )
                    }
                }
            }
        }
    }

    fun deletePreset(preset: Preset, scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            dao.deletePreset(preset)
        }
    }

    fun updatePreset(oldPreset: Preset, newPreset: Preset, scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            if (oldPreset.name != newPreset.name) {
                dao.deletePreset(oldPreset)
            }
            dao.insertPreset(newPreset)
        }
    }

    fun resetToDefaults() {
        if (!isRunning) {
            initialDurationSec = 2700
            timeLeftSec = 2700
            intervalBells.clear()
            intervalBells.addAll(
                listOf(
                    IntervalBell(atSecFromStart = 900, soundType = "interval_bell", repeats = 1, volume = 0.5f),
                    IntervalBell(atSecFromStart = 1800, soundType = "interval_bell", repeats = 2, volume = 0.6f),
                    IntervalBell(atSecFromStart = 2699, soundType = "interval_bell", repeats = 3, volume = 0.7f)
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

    fun startTimer(scope: kotlinx.coroutines.CoroutineScope, onPlayBell: (String, Int, Float) -> Unit = { _, _, _ -> }) {
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
                onPlayBell("starting_bell", 1, startingBellVolume)
            }

            val snapshotBells = intervalBells.toList()

            while (timeLeftSec > 0 && isRunning) {
                delay(1000)
                timeLeftSec--

                val elapsedSec = initialDurationSec - timeLeftSec
                snapshotBells.forEach { bell ->
                    if (elapsedSec == bell.atSecFromStart) {
                        onPlayBell(bell.soundType, bell.repeats, bell.volume)
                    }
                }
            }
            if (timeLeftSec <= 0 && isRunning) {
                onPlayBell("starting_bell", 1, startingBellVolume)
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

    fun deleteMeditation(meditation: Meditation, scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            dao.delete(meditation)
        }
    }

    fun clearHistory(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            dao.deleteAll()
        }
    }

    fun exportToObsidian(context: Context, history: List<Meditation>) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val content = StringBuilder("# Zendence Meditation History\n\n")
        content.append("| Date | Duration (min) |\n")
        content.append("| :--- | :--- |\n")
        history.forEach {
            content.append("| ${sdf.format(Date(it.timestamp))} | ${it.durationMinutes} |\n")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_SUBJECT, "Zendence Meditation History")
            putExtra(Intent.EXTRA_TEXT, content.toString())
        }
        context.startActivity(Intent.createChooser(intent, "Export History"))
    }
}

// --- 3. UI COMPONENTS ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeditationApp(vm: MeditationViewModel) {
    val context = LocalContext.current
    val history by vm.history.collectAsState(initial = emptyList())
    val presets by vm.presets.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var presetToEdit by remember { mutableStateOf<Preset?>(null) }
    var presetToDelete by remember { mutableStateOf<Preset?>(null) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    
    var isEditingDuration by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf("") }
    var isBellsExpanded by remember { mutableStateOf(false) }
    
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

    fun playBell(type: String, repeats: Int = 1, bellVolume: Float = 1.0f) {
        val resId = when (type) {
            "starting_bell" -> R.raw.starting_bell
            "interval_bell" -> R.raw.interval_bell
            else -> R.raw.starting_bell
        }
        val mediaItem = MediaItem.fromUri("android.resource://${context.packageName}/$resId")
        bellPlayer.stop()
        bellPlayer.clearMediaItems()
        bellPlayer.volume = bellVolume
        repeat(repeats) {
            bellPlayer.addMediaItem(mediaItem)
        }
        bellPlayer.prepare()
        bellPlayer.play()
    }

    LaunchedEffect(vm.volume) {
        exoPlayer.volume = vm.volume
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
            onConfirm = { time, sound, repeats, vol ->
                vm.intervalBells.add(IntervalBell(atSecFromStart = time, soundType = sound, repeats = repeats, volume = vol))
                showIntervalDialog = false
            },
            maxSec = vm.initialDurationSec
        )
    }

    val selectedMeditationIds = remember { mutableStateListOf<Int>() }
    var isSelectionMode by remember { mutableStateOf(false) }

    if (showSavePresetDialog) {
        SavePresetDialog(
            onDismiss = { showSavePresetDialog = false },
            onConfirm = { name ->
                vm.savePreset(name, scope)
                showSavePresetDialog = false
            }
        )
    }

    if (presetToEdit != null) {
        EditPresetDialog(
            preset = presetToEdit!!,
            onDismiss = { presetToEdit = null },
            onConfirm = { updatedPreset ->
                vm.updatePreset(presetToEdit!!, updatedPreset, scope)
                presetToEdit = null
            }
        )
    }

    if (presetToDelete != null) {
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            title = { Text("Delete Preset") },
            text = { Text("Are you sure you want to delete '${presetToDelete?.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        presetToDelete?.let { vm.deletePreset(it, scope) }
                        presetToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { presetToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                if (!isEditingDuration && keyEvent.key == Key.Spacebar) {
                    if (keyEvent.type == KeyEventType.KeyUp) {
                        vm.toggleTimer(scope) { type, repeats, vol -> playBell(type, repeats, vol) }
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
        // Add a dark scrim to make white text/icons pop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 40.dp)) {
                    val currentDateTime = remember {
                        val sdf = SimpleDateFormat("EEE, MMM d\nh:mm a", Locale.getDefault())
                        sdf.format(Date())
                    }
                    Text(
                        text = currentDateTime,
                        style = TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 8f)
                        ),
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "ZENDENCE",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 8.sp,
                                color = MaterialTheme.colorScheme.primary,
                                shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 12f)
                            )
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                    actions = {
                        IconButton(onClick = { activity?.finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Exit", tint = MaterialTheme.colorScheme.onSurface)
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
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        )
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!vm.isRunning) {
                                    IconButton(onClick = { vm.decrementDuration() }) {
                                        Icon(Icons.Rounded.KeyboardArrowLeft, contentDescription = "Minus", tint = MaterialTheme.colorScheme.onSurface)
                                    }
                                }

                                if (isEditingDuration && !vm.isRunning) {
                                    OutlinedTextField(
                                        value = editValue,
                                        onValueChange = { 
                                            editValue = it
                                            val mins = it.toIntOrNull() ?: 0
                                            if (mins > 0) vm.updateDuration(mins)
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.width(100.dp),
                                        singleLine = true,
                                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 32.sp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            cursorColor = MaterialTheme.colorScheme.primary
                                        ),
                                        keyboardActions = KeyboardActions(onDone = {
                                            isEditingDuration = false
                                        })
                                    )
                                } else {
                                    Text(
                                        text = String.format(Locale.getDefault(), "%02d:%02d", vm.timeLeftSec / 60, vm.timeLeftSec % 60),
                                        style = TextStyle(
                                            fontSize = 64.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            shadow = Shadow(color = Color.Black.copy(alpha = 0.3f), blurRadius = 12f)
                                        ),
                                        modifier = Modifier.clickable(enabled = !vm.isRunning) {
                                            editValue = (vm.initialDurationSec / 60).toString()
                                            isEditingDuration = true
                                        }
                                    )
                                }

                                if (!vm.isRunning) {
                                    IconButton(onClick = { vm.incrementDuration() }) {
                                        Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = "Plus", tint = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }

                            Text(
                                text = vm.dailyQuote,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                ),
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(0.8f)
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

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "SESSIONS",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.tertiary,
                                shadow = Shadow(color = Color.Black.copy(alpha = 0.8f), blurRadius = 8f)
                            )
                        )
                        IconButton(onClick = { showSavePresetDialog = true }, enabled = !vm.isRunning) {
                            Icon(Icons.Default.Save, contentDescription = "Save Preset", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    
                    if (presets.isEmpty()) {
                        Text("No saved presets", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 12.sp)
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 200.dp).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(presets) { preset ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable { vm.loadPreset(preset) },
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(preset.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.3f), blurRadius = 4f)))
                                            val bellCount = if (preset.intervalBellsJson.isEmpty()) 0 else preset.intervalBellsJson.split("|").size
                                            val bellInfo = if (bellCount > 0) {
                                                val firstBell = preset.intervalBellsJson.split("|")[0].split(":")
                                                val firstTime = firstBell[0].toInt()
                                                val firstVol = if (firstBell.size == 4) firstBell[3].toFloatOrNull() ?: 0.7f else 0.7f
                                                " | $bellCount bells (1st @ ${firstTime / 60}m, Vol: ${(firstVol * 100).toInt()}%)"
                                            } else ""
                                            Text("${preset.durationMin}m | Music: ${(preset.volume * 100).toInt()}%$bellInfo", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 11.sp)
                                        }
                                        IconButton(onClick = { presetToEdit = preset }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(onClick = { presetToDelete = preset }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(24.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Music Volume", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Slider(
                                    value = vm.volume,
                                    onValueChange = { vm.volume = it },
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    ),
                                    thumb = {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "${(vm.volume * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                                    .offset(y = (-24).dp)
                                            )
                                            SliderDefaults.Thumb(
                                                interactionSource = remember { MutableInteractionSource() },
                                                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { isBellsExpanded = !isBellsExpanded },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isBellsExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Bells & Intervals", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.weight(1f))
                                if (!isBellsExpanded) {
                                    val count = vm.intervalBells.count { it.atSecFromStart < vm.initialDurationSec }
                                    Text(
                                        "${if (vm.startingBellEnabled) "Start/End + " else ""}$count intervals",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            if (isBellsExpanded) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Starting/Ending Bell", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f), fontSize = 14.sp)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Switch(
                                        checked = vm.startingBellEnabled,
                                        onCheckedChange = { vm.startingBellEnabled = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                        ),
                                        modifier = Modifier.scale(0.8f)
                                    )
                                }

                                if (vm.startingBellEnabled) {
                                    Column(modifier = Modifier.padding(top = 8.dp)) {
                                        Text(
                                            "Bell Volume: ${(vm.startingBellVolume * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Slider(
                                            value = vm.startingBellVolume,
                                            onValueChange = { vm.startingBellVolume = it },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                
                                if (vm.intervalBells.any { it.atSecFromStart < vm.initialDurationSec }) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Interval Bells", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    
                                    vm.intervalBells.filter { it.atSecFromStart < vm.initialDurationSec }.forEach { bell ->
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                            val repeatSuffix = if (bell.repeats > 1) " (x${bell.repeats})" else ""
                                            Text("${bell.atSecFromStart / 60}m ${bell.atSecFromStart % 60}s", color = Color.White)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(bell.soundType.replace("_", " "), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                            Text(repeatSuffix, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("${(bell.volume * 100).toInt()}%", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                            Spacer(modifier = Modifier.weight(1f))
                                            IconButton(onClick = { vm.intervalBells.remove(bell) }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
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
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { vm.toggleTimer(scope) { type, repeats, vol -> playBell(type, repeats, vol) } },
                            modifier = Modifier.weight(1f).height(64.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
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
                                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
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
                            Text("Enable Do Not Disturb for silence", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                    }
                }

                item {
                    val streakInfo = remember(history) {
                        if (history.isEmpty()) return@remember "0 sessions"
                        var streakCount = 0
                        var lastCal: Calendar? = null
                        for (s in history) {
                            val curCal = Calendar.getInstance().apply { timeInMillis = s.timestamp }
                            if (lastCal != null) {
                                val isSameDay = curCal.get(Calendar.YEAR) == lastCal.get(Calendar.YEAR) &&
                                              curCal.get(Calendar.DAY_OF_YEAR) == lastCal.get(Calendar.DAY_OF_YEAR)
                                val nextDay = (curCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
                                val isContiguous = nextDay.get(Calendar.YEAR) == lastCal.get(Calendar.YEAR) &&
                                                   nextDay.get(Calendar.DAY_OF_YEAR) == lastCal.get(Calendar.DAY_OF_YEAR)
                                if (!isSameDay && !isContiguous) break
                            }
                            streakCount++
                            lastCal = curCal
                        }
                        if (streakCount > 1) "$streakCount session streak • ${history.size} total"
                        else "${history.size} sessions"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "JOURNAL",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                shadow = Shadow(color = Color.Black.copy(alpha = 0.8f), blurRadius = 8f)
                            )
                        )
                        Text(streakInfo, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 12.sp)
                        Row {
                            if (isSelectionMode) {
                                TextButton(onClick = {
                                    if (selectedMeditationIds.size == history.size) {
                                        selectedMeditationIds.clear()
                                    } else {
                                        selectedMeditationIds.clear()
                                        selectedMeditationIds.addAll(history.map { it.id })
                                    }
                                }) {
                                    Text(if (selectedMeditationIds.size == history.size) "Deselect All" else "Select All", fontSize = 12.sp)
                                }
                                TextButton(onClick = {
                                    val selectedSessions = history.filter { it.id in selectedMeditationIds }
                                    if (selectedSessions.isNotEmpty()) {
                                        vm.exportToObsidian(context, selectedSessions)
                                    }
                                    isSelectionMode = false
                                    selectedMeditationIds.clear()
                                }) {
                                    Text("Export (${selectedMeditationIds.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                IconButton(onClick = { 
                                    isSelectionMode = false
                                    selectedMeditationIds.clear()
                                }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Cancel", modifier = Modifier.size(18.dp))
                                }
                            } else if (history.isNotEmpty()) {
                                TextButton(onClick = { isSelectionMode = true }) {
                                    Text("Select", fontSize = 12.sp)
                                }
                                TextButton(onClick = { showClearHistoryDialog = true }) {
                                    Text("Clear All", color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                }

                items(history) { session ->
                    val date = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(session.timestamp))
                    
                    val primary = MaterialTheme.colorScheme.primary
                    val secondary = MaterialTheme.colorScheme.secondary
                    val tertiary = MaterialTheme.colorScheme.tertiary
                    val pContainer = MaterialTheme.colorScheme.primaryContainer
                    val sContainer = MaterialTheme.colorScheme.secondaryContainer
                    val tContainer = MaterialTheme.colorScheme.tertiaryContainer
                    
                    val groupColor = remember(session.timestamp, history, primary, secondary, tertiary, pContainer, sContainer, tContainer) {
                        var groupIndex = 0
                        var lastCalendar: Calendar? = null
                        
                        // We need the history in chronological order to determine groups
                        val sortedHistory = history.sortedBy { it.timestamp } 
                        
                        for (s in sortedHistory) {
                            val currentCalendar = Calendar.getInstance().apply { timeInMillis = s.timestamp }
                            if (lastCalendar != null) {
                                val isSameDay = currentCalendar.get(Calendar.YEAR) == lastCalendar.get(Calendar.YEAR) &&
                                              currentCalendar.get(Calendar.DAY_OF_YEAR) == lastCalendar.get(Calendar.DAY_OF_YEAR)
                                
                                val nextDayCalendar = (lastCalendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
                                val isContiguous = nextDayCalendar.get(Calendar.YEAR) == currentCalendar.get(Calendar.YEAR) &&
                                                   nextDayCalendar.get(Calendar.DAY_OF_YEAR) == currentCalendar.get(Calendar.DAY_OF_YEAR)

                                if (!isSameDay && !isContiguous) {
                                    groupIndex++
                                }
                            }
                            if (s.id == session.id) break
                            lastCalendar = currentCalendar
                        }
                        
                        val colors = listOf(
                            primary,
                            secondary,
                            tertiary,
                            pContainer,
                            sContainer,
                            tContainer
                        )
                        colors[groupIndex % colors.size].copy(alpha = 0.5f)
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                if (isSelectionMode) {
                                    if (selectedMeditationIds.contains(session.id)) {
                                        selectedMeditationIds.remove(session.id)
                                    } else {
                                        selectedMeditationIds.add(session.id)
                                    }
                                }
                            },
                        color = if (isSelectionMode && selectedMeditationIds.contains(session.id)) 
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp),
                        border = if (isSelectionMode && selectedMeditationIds.contains(session.id))
                                    androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                 else androidx.compose.foundation.BorderStroke(1.dp, groupColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelectionMode) {
                                Checkbox(
                                    checked = selectedMeditationIds.contains(session.id),
                                    onCheckedChange = { checked ->
                                        if (checked == true) selectedMeditationIds.add(session.id)
                                        else selectedMeditationIds.remove(session.id)
                                    },
                                    modifier = Modifier.scale(0.8f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            
                            Box(
                                modifier = Modifier.size(40.dp).background(groupColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${session.durationMinutes}",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 4f))
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Meditation Session", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.3f), blurRadius = 4f)))
                                Text(date, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            if (!isSelectionMode) {
                                IconButton(onClick = { vm.deleteMeditation(session, scope) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear Journal") },
            text = { Text("Are you sure you want to delete all session history? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearHistory(scope)
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
    val oldEntries = history.filter { it.timestamp < thirtyDaysAgo }
    if (oldEntries.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Manage Old Entries") },
            text = { Text("You have ${oldEntries.size} entries older than 30 days. Would you like to clear them or export them?") },
            confirmButton = {
                Row {
                    TextButton(onClick = { 
                        vm.exportToObsidian(context, history)
                    }) {
                        Text("Export")
                    }
                    TextButton(
                        onClick = { 
                            scope.launch {
                                oldEntries.forEach { vm.deleteMeditation(it, scope) }
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) {
                        Text("Clear Old")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { /* Could add a 'remind me later' logic here */ }) {
                    Text("Ignore")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPresetDialog(
    preset: Preset,
    onDismiss: () -> Unit,
    onConfirm: (Preset) -> Unit
) {
    var name by remember { mutableStateOf(preset.name) }
    var durationMin by remember { mutableStateOf(preset.durationMin.toString()) }
    var volume by remember { mutableFloatStateOf(preset.volume) }
    
    val initialBells = remember(preset.intervalBellsJson) {
        if (preset.intervalBellsJson.isEmpty()) emptyList()
        else preset.intervalBellsJson.split("|").mapNotNull {
            val parts = it.split(":")
            if (parts.size >= 3) {
                val vol = if (parts.size == 4) parts[3].toFloatOrNull() ?: 0.7f else 0.7f
                IntervalBell(atSecFromStart = parts[0].toInt(), repeats = parts[1].toInt(), soundType = parts[2], volume = vol)
            } else null
        }
    }
    val bells = remember { mutableStateListOf<IntervalBell>().apply { addAll(initialBells) } }
    var showAddBell by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Preset") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = durationMin,
                    onValueChange = { durationMin = it },
                    label = { Text("Duration (minutes)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    Text("Volume: ${(volume * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = volume,
                        onValueChange = { volume = it },
                        modifier = Modifier.fillMaxWidth(),
                        thumb = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${(volume * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                        .offset(y = (-24).dp)
                                )
                                SliderDefaults.Thumb(
                                    interactionSource = remember { MutableInteractionSource() },
                                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    )
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Text("Interval Bells", style = MaterialTheme.typography.titleSmall)
                
                bells.forEachIndexed { index, bell ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${bell.atSecFromStart / 60}m ${bell.atSecFromStart % 60}s", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${(bell.volume * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { bells.remove(bell) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Bell", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                            }
                        }
                        Slider(
                            value = bell.volume,
                            onValueChange = { newVol ->
                                bells[index] = bell.copy(volume = newVol)
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                TextButton(onClick = { showAddBell = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Bell")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val d = durationMin.toIntOrNull() ?: preset.durationMin
                val bellsJson = bells.joinToString("|") { "${it.atSecFromStart}:${it.repeats}:${it.soundType}:${it.volume}" }
                onConfirm(preset.copy(name = name, durationMin = d, volume = volume, intervalBellsJson = bellsJson))
            }) { Text("Save Changes") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
    
    if (showAddBell) {
        val maxSec = (durationMin.toIntOrNull() ?: 0) * 60
        IntervalBellDialog(
            onDismiss = { showAddBell = false },
            onConfirm = { time, sound, repeats, vol ->
                bells.add(IntervalBell(atSecFromStart = time, soundType = sound, repeats = repeats, volume = vol))
                showAddBell = false
            },
            maxSec = if (maxSec > 0) maxSec else 3600
        )
    }
}

@Composable
fun SavePresetDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Preset") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Preset Name (e.g., Morning Zen)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun IntervalBellDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int, String, Int, Float) -> Unit,
    maxSec: Int
) {
    var timeMin by remember { mutableStateOf("1") }
    var timeSec by remember { mutableStateOf("0") }
    var repeats by remember { mutableStateOf("1") }
    var soundType by remember { mutableStateOf("interval_bell") }
    var bellVolume by remember { mutableFloatStateOf(0.7f) }
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

                Text("Volume: ${(bellVolume * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = bellVolume,
                    onValueChange = { bellVolume = it },
                    valueRange = 0f..1f,
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
                    onConfirm(totalSec, soundType, repeatCount, bellVolume)
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
            ZendenceTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MeditationApp(vm)
                }
            }
        }
    }
}
