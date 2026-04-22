@file:OptIn(ExperimentalMaterial3Api::class)
package com.alex.zendence

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
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
import androidx.room.Room
import com.alex.zendence.ui.theme.ZendenceTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

class MeditationViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val db = Room.databaseBuilder(application, AppDatabase::class.java, "meditation-db")
        .fallbackToDestructiveMigration()
        .build()
    private val dao = db.meditationDao()
    private val notificationManager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val history = dao.getAll()
    val presets = dao.getAllPresets()
    
    var dailyQuote by mutableStateOf("Loading wisdom...")
    
    private val sharedPrefs = application.getSharedPreferences("zendence_prefs", Context.MODE_PRIVATE)

    var isRunning by mutableStateOf(false)
    var initialDurationSec by mutableIntStateOf(sharedPrefs.getInt("initial_duration_sec", 2700))
    var timeLeftSec by mutableIntStateOf(sharedPrefs.getInt("initial_duration_sec", 2700))
    var volume by mutableFloatStateOf(sharedPrefs.getFloat("volume", 0.5f))

    var startingBellEnabled by mutableStateOf(sharedPrefs.getBoolean("starting_bell_enabled", true))
    var startingBellVolume by mutableFloatStateOf(sharedPrefs.getFloat("starting_bell_volume", 0.7f))
    val intervalBells = mutableStateListOf<IntervalBell>()

    private var meditationService: MeditationService? = null
    private var isBound = false

    var activePresetName by mutableStateOf<String?>(null)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MeditationService.LocalBinder
            meditationService = binder.getService()
            isBound = true
            observeService()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            meditationService = null
        }
    }

    init {
        fetchQuote()
        loadSettings()
        Intent(application, MeditationService::class.java).also { intent ->
            application.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun observeService() {
        viewModelScope.launch {
            meditationService?.isRunning?.collectLatest { running ->
                isRunning = running
            }
        }
        viewModelScope.launch {
            meditationService?.timeLeftSec?.collectLatest { seconds ->
                if (isRunning) {
                    timeLeftSec = seconds
                }
            }
        }
        viewModelScope.launch {
            meditationService?.lastSavedMeditationId?.collectLatest { id ->
                id?.let { meditationId ->
                    val meditation = dao.getById(meditationId.toInt())
                    if (meditation != null) {
                        lastSessionMinutes = meditation.durationMinutes
                        editingMeditation = meditation
                        showInsightDialog = true
                    }
                }
            }
        }
    }

    private fun loadSettings() {
        val bellsJson = sharedPrefs.getString("interval_bells", null)
        if (bellsJson != null && bellsJson.isNotEmpty()) {
            loadIntervalBellsFromJson(bellsJson)
        } else {
            intervalBells.addAll(
                listOf(
                    IntervalBell(atSecFromStart = 900, soundType = "interval_bell", repeats = 1, volume = 0.5f),
                    IntervalBell(atSecFromStart = 1800, soundType = "interval_bell", repeats = 2, volume = 0.6f),
                    IntervalBell(atSecFromStart = 2699, soundType = "interval_bell", repeats = 3, volume = 0.7f)
                )
            )
        }
    }

    fun fetchQuote() {
        viewModelScope.launch {
            try {
                val response = java.net.URL("https://api.quotable.io/random?tags=spirituality|wisdom|faith").readText()
                val content = response.substringAfter("\"content\":\"").substringBefore("\"")
                val author = response.substringAfter("\"author\":\"").substringBefore("\"")
                dailyQuote = "\"$content\" — $author"
            } catch (e: Exception) {
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

    fun saveCurrentSettings() {
        val bellsJson = intervalBells.joinToString("|") { "${it.atSecFromStart}:${it.repeats}:${it.soundType}:${it.volume}" }
        sharedPrefs.edit()
            .putInt("initial_duration_sec", initialDurationSec)
            .putFloat("volume", volume)
            .putBoolean("starting_bell_enabled", startingBellEnabled)
            .putFloat("starting_bell_volume", startingBellVolume)
            .putString("interval_bells", bellsJson)
            .apply()
    }

    private fun loadIntervalBellsFromJson(json: String) {
        intervalBells.clear()
        json.split("|").forEach {
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

    fun toggleTimer() {
        if (isRunning) {
            meditationService?.stopMeditation()
        } else {
            val intent = Intent(getApplication(), MeditationService::class.java)
            getApplication<android.app.Application>().startForegroundService(intent)
            meditationService?.startMeditation(
                durationSec = initialDurationSec,
                bells = intervalBells.toList(),
                startBellEnabled = startingBellEnabled,
                startBellVolume = startingBellVolume,
                bgVolume = volume
            )
        }
    }

    fun savePreset(name: String, scope: CoroutineScope) {
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
            activePresetName = preset.name
            initialDurationSec = preset.durationMin * 60
            timeLeftSec = initialDurationSec
            volume = preset.volume
            loadIntervalBellsFromJson(preset.intervalBellsJson)
            saveCurrentSettings()
        }
    }

    fun updateActivePresetIfAny(scope: CoroutineScope) {
        val currentName = activePresetName ?: return
        val bellsJson = intervalBells
            .filter { it.atSecFromStart < initialDurationSec }
            .joinToString("|") { "${it.atSecFromStart}:${it.repeats}:${it.soundType}:${it.volume}" }
        val preset = Preset(
            name = currentName,
            durationMin = initialDurationSec / 60,
            volume = volume,
            intervalBellsJson = bellsJson
        )
        scope.launch {
            dao.insertPreset(preset)
        }
    }

    fun deletePreset(preset: Preset, scope: CoroutineScope) {
        scope.launch {
            dao.deletePreset(preset)
        }
    }

    fun resetToDefaults() {
        if (!isRunning) {
            initialDurationSec = 2700
            timeLeftSec = 2700
            volume = 0.5f
            startingBellEnabled = true
            startingBellVolume = 0.7f
            intervalBells.clear()
            intervalBells.addAll(
                listOf(
                    IntervalBell(atSecFromStart = 900, soundType = "interval_bell", repeats = 1, volume = 0.5f),
                    IntervalBell(atSecFromStart = 1800, soundType = "interval_bell", repeats = 2, volume = 0.6f),
                    IntervalBell(atSecFromStart = 2699, soundType = "interval_bell", repeats = 3, volume = 0.7f)
                )
            )
            saveCurrentSettings()
        }
    }

    fun incrementDuration() {
        if (!isRunning) {
            initialDurationSec += 60
            timeLeftSec = initialDurationSec
            saveCurrentSettings()
        }
    }

    fun decrementDuration() {
        if (!isRunning && initialDurationSec > 60) {
            initialDurationSec -= 60
            timeLeftSec = initialDurationSec
            saveCurrentSettings()
        }
    }

    fun updateDuration(minutes: Int) {
        if (!isRunning && minutes > 0) {
            initialDurationSec = minutes * 60
            timeLeftSec = initialDurationSec
            saveCurrentSettings()
        }
    }

    fun updateServiceVolume(vol: Float) {
        meditationService?.updateVolume(vol)
    }

    var showInsightDialog by mutableStateOf(false)
    var editingMeditation by mutableStateOf<Meditation?>(null)
    var lastSessionMinutes by mutableIntStateOf(0)

    fun onEditInsight(meditation: Meditation) {
        editingMeditation = meditation
        showInsightDialog = true
    }

    fun onInsightSubmitted(insight: String, scope: CoroutineScope) {
        val meditation = editingMeditation
        viewModelScope.launch(Dispatchers.IO) {
            meditation?.let {
                val updated = it.copy(insight = insight.ifBlank { null })
                dao.update(updated)
                withContext(Dispatchers.Main) {
                    if (editingMeditation?.id == updated.id) {
                        editingMeditation = updated
                    }
                }
            }
        }
        showInsightDialog = false
        editingMeditation = null
    }

    fun onInsightDismissed() {
        showInsightDialog = false
        editingMeditation = null
    }

    fun deleteMeditation(meditation: Meditation, scope: CoroutineScope) {
        scope.launch {
            dao.delete(meditation)
        }
    }

    fun clearHistory(scope: CoroutineScope) {
        scope.launch {
            dao.deleteAll()
        }
    }

    fun exportToObsidian(context: Context, history: List<Meditation>) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val content = StringBuilder("# Zendence Meditation History\n\n")
        content.append("| Date | Duration (min) | Insight |\n")
        content.append("| :--- | :--- | :--- |\n")
        history.forEach {
            val insight = it.insight ?: ""
            content.append("| ${sdf.format(Date(it.timestamp))} | ${it.durationMinutes} | $insight |\n")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_SUBJECT, "Zendence Meditation History")
            putExtra(Intent.EXTRA_TEXT, content.toString())
        }
        context.startActivity(Intent.createChooser(intent, "Export History"))
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<android.app.Application>().unbindService(connection)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        setContent {
            ZendenceTheme {
                MeditationApp()
            }
        }
    }
}

@Composable
fun MeditationApp(vm: MeditationViewModel = viewModel()) {
    val context = LocalContext.current
    val history by vm.history.collectAsState(initial = emptyList())
    val presets by vm.presets.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showIntervalDialog by remember { mutableStateOf(false) }
    var editingBell by remember { mutableStateOf<IntervalBell?>(null) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var presetToEdit by remember { mutableStateOf<Preset?>(null) }
    var presetToDelete by remember { mutableStateOf<Preset?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showFullReading by remember { mutableStateOf(false) }
    
    var isEditingDuration by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf("") }
    var isBellsExpanded by remember { mutableStateOf(false) }
    
    val activity = context as? ComponentActivity
    SideEffect {
        if (vm.isRunning) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.zendence_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
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
                    Column(modifier = Modifier.align(Alignment.TopStart)) {
                        Text(
                            text = currentDateTime,
                            style = TextStyle(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Light,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 8f)
                            )
                        )
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        )
                    }
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
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shadow = Shadow(color = Color.Black, blurRadius = 8f)
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
                                    color = Color(0xFFFF69B4),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                ),
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .fillMaxWidth(0.8f)
                                    .clickable { showFullReading = true }
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
                                                val details = preset.intervalBellsJson.split("|").joinToString(", ") {
                                                    val p = it.split(":")
                                                    if (p.size >= 4) "${p[0]}s (${(p[3].toFloat()*100).toInt()}%)" else "${p[0]}s"
                                                }
                                                "$bellCount bells: $details"
                                            } else "no bells"
                                            Text("${preset.durationMin}m • $bellInfo", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
                                        }
                                        IconButton(onClick = { 
                                            presetToEdit = preset
                                            vm.loadPreset(preset)
                                            showSettings = true
                                            isBellsExpanded = true
                                            showSavePresetDialog = true
                                        }, enabled = !vm.isRunning) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Preset", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                        }
                                        IconButton(onClick = { presetToDelete = preset }, enabled = !vm.isRunning) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                                IconButton(onClick = { showSettings = !showSettings }) {
                                    Icon(if (showSettings) Icons.Rounded.ExpandLess else Icons.Rounded.Settings, contentDescription = "Toggle Settings", tint = MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(onClick = { showHistory = !showHistory }) {
                                    Icon(if (showHistory) Icons.Rounded.ExpandLess else Icons.Rounded.History, contentDescription = "Toggle History", tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        if (showSettings) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                    Column {
                                        Text("Music Volume", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                        Slider(
                                            value = vm.volume,
                                            onValueChange = { 
                                                vm.volume = it 
                                                vm.updateServiceVolume(it)
                                            },
                                            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Starting Bell", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                            Text("Play bell when meditation begins", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        }
                                        Switch(checked = vm.startingBellEnabled, onCheckedChange = { vm.startingBellEnabled = it })
                                    }

                                    if (vm.startingBellEnabled) {
                                        Column {
                                            Text("Bell Volume", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Slider(value = vm.startingBellVolume, onValueChange = { vm.startingBellVolume = it })
                                        }
                                    }

                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable { isBellsExpanded = !isBellsExpanded },
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Interval Bells (${vm.intervalBells.size})", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                            Icon(if (isBellsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
                                        }

                                        if (isBellsExpanded) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            vm.intervalBells.forEach { bell ->
                                                Surface(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                    color = Color.White.copy(alpha = 0.05f),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Column(modifier = Modifier.weight(1f).clickable {
                                                            editingBell = bell
                                                            showIntervalDialog = true
                                                        }) {
                                                            Text("${bell.atSecFromStart / 60}m ${bell.atSecFromStart % 60}s", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                                                            Text("${bell.repeats}x • ${bell.soundType.replace("_", " ")}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                                        }
                                                        IconButton(onClick = { 
                                                            vm.intervalBells.remove(bell)
                                                            vm.saveCurrentSettings()
                                                            vm.updateActivePresetIfAny(scope)
                                                        }) {
                                                            Icon(Icons.Rounded.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }
                                            Button(
                                                onClick = { showIntervalDialog = true },
                                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                            ) {
                                                Icon(Icons.Rounded.Add, contentDescription = null)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Add Interval Bell", color = MaterialTheme.colorScheme.onSecondaryContainer)
                                            }
                                        }
                                    }
                                    
                                    Button(
                                        onClick = { vm.resetToDefaults() },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Reset to Defaults", color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            }
                        }

                        if (showHistory) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Recent Sessions", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                        Row {
                                            IconButton(onClick = { vm.exportToObsidian(context, history) }) {
                                                Icon(Icons.Rounded.Share, contentDescription = "Export History", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { showClearHistoryDialog = true }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Clear History", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                    
                                    if (history.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                            Text("No sessions yet. Time to meditate?", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 14.sp)
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
                                                            IconButton(onClick = { vm.onEditInsight(session) }) {
                                                                Icon(Icons.Default.Edit, contentDescription = "Edit Insight", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                                            }
                                                            IconButton(onClick = { vm.deleteMeditation(session, scope) }) {
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
                                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
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
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }

        FloatingActionButton(
            onClick = { vm.toggleTimer() },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp).size(80.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 12.dp, pressedElevation = 4.dp)
        ) {
            Icon(
                if (vm.isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                contentDescription = if (vm.isRunning) "Stop" else "Start",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        if (showIntervalDialog) {
                                            var bellAtMin by remember { mutableStateOf(editingBell?.let { (it.atSecFromStart / 60).toString() } ?: "") }
                                            var bellAtSec by remember { mutableStateOf(editingBell?.let { (it.atSecFromStart % 60).toString() } ?: "") }
                                            var repeats by remember { mutableStateOf(editingBell?.repeats?.toString() ?: "1") }
                                            var type by remember { mutableStateOf(editingBell?.soundType ?: "interval_bell") }
                                            var bellVol by remember { mutableStateOf(editingBell?.volume ?: 0.7f) }

                                            AlertDialog(
                                                onDismissRequest = { 
                                                    showIntervalDialog = false
                                                    editingBell = null
                                                },
                                                title = { Text(if (editingBell != null) "Edit Interval Bell" else "Add Interval Bell") },
                                                text = {
                                                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            OutlinedTextField(value = bellAtMin, onValueChange = { bellAtMin = it }, label = { Text("Min") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                                            OutlinedTextField(value = bellAtSec, onValueChange = { bellAtSec = it }, label = { Text("Sec") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                                        }
                                                        OutlinedTextField(value = repeats, onValueChange = { repeats = it }, label = { Text("Repeats") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                                        
                                                        Column {
                                                            Text("Bell Volume: ${(bellVol * 100).toInt()}%", fontSize = 12.sp)
                                                            Slider(value = bellVol, onValueChange = { bellVol = it })
                                                        }
                                                    }
                                                },
                                                confirmButton = {
                                                    Button(onClick = {
                                                        val totalSec = (bellAtMin.toIntOrNull() ?: 0) * 60 + (bellAtSec.toIntOrNull() ?: 0)
                                                        if (totalSec > 0) {
                                                            val newBell = IntervalBell(
                                                                id = editingBell?.id ?: UUID.randomUUID().toString(),
                                                                atSecFromStart = totalSec, 
                                                                soundType = type, 
                                                                repeats = repeats.toIntOrNull() ?: 1, 
                                                                volume = bellVol
                                                            )
                                                            editingBell?.let { old ->
                                                                val index = vm.intervalBells.indexOfFirst { it.id == old.id }
                                                                if (index != -1) vm.intervalBells[index] = newBell
                                                            } ?: run {
                                                                vm.intervalBells.add(newBell)
                                                            }
                                                            vm.intervalBells.sortBy { it.atSecFromStart }
                                                            vm.saveCurrentSettings()
                                                            vm.updateActivePresetIfAny(scope)
                                                        }
                                                        showIntervalDialog = false
                                                        editingBell = null
                                                    }) { Text(if (editingBell != null) "Update" else "Add") }
                                                }
                                            )
                                        }

                                        if (showSavePresetDialog) {
                                            var name by remember { mutableStateOf(presetToEdit?.name ?: "") }
                                            AlertDialog(
                                                onDismissRequest = { 
                                                    showSavePresetDialog = false
                                                    presetToEdit = null
                                                },
                                                title = { Text(if (presetToEdit != null) "Edit Preset" else "Save Preset") },
                                                text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Preset Name") }, enabled = presetToEdit == null) },
                                                confirmButton = {
                                                    Button(onClick = {
                                                        if (name.isNotBlank()) {
                                                            vm.savePreset(name, scope)
                                                            showSavePresetDialog = false
                                                            presetToEdit = null
                                                        }
                                                    }) { Text(if (presetToEdit != null) "Update" else "Save") }
                                                }
                                            )
                                        }

        if (presetToDelete != null) {
            AlertDialog(
                onDismissRequest = { presetToDelete = null },
                title = { Text("Delete Preset") },
                text = { Text("Delete '${presetToDelete?.name}'?") },
                confirmButton = {
                    TextButton(onClick = {
                        presetToDelete?.let { vm.deletePreset(it, scope) }
                        presetToDelete = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { presetToDelete = null }) { Text("Cancel") } }
            )
        }

        if (showClearHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showClearHistoryDialog = false },
                title = { Text("Clear History") },
                text = { Text("Delete all your meditation history? This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.clearHistory(scope)
                        showClearHistoryDialog = false
                    }) { Text("Clear All", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") } }
            )
        }

        if (vm.showInsightDialog) {
            var insightText by remember(vm.editingMeditation) { mutableStateOf(vm.editingMeditation?.insight ?: "") }
            AlertDialog(
                onDismissRequest = { vm.onInsightDismissed() },
                title = { Text(if (vm.editingMeditation?.insight == null) "Session Complete" else "Edit Insight") },
                text = {
                    Column {
                        if (vm.editingMeditation?.insight == null) {
                            Text("Great job! You meditated for ${vm.lastSessionMinutes} minutes.", modifier = Modifier.padding(bottom = 12.dp))
                        }
                        OutlinedTextField(
                            value = insightText,
                            onValueChange = { insightText = it },
                            label = { Text("How do you feel?") },
                            placeholder = { Text("e.g. Peaceful, focused, restless...") },
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            maxLines = 5
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { vm.onInsightSubmitted(insightText, scope) }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { vm.onInsightDismissed() }) {
                        Text(if (vm.editingMeditation?.insight == null) "Skip" else "Cancel")
                    }
                }
            )
        }

        if (showFullReading) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showFullReading = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.95f))
                        .clickable { showFullReading = false },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 40.dp, vertical = 60.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.FormatQuote,
                            contentDescription = null,
                            tint = Color(0xFFFF69B4),
                            modifier = Modifier.size(48.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        val meditationText = remember {
                            try {
                                context.resources.openRawResource(R.raw.meditation).bufferedReader().use { it.readText() }
                            } catch (e: Exception) {
                                ""
                            }
                        }
                        
                        Text(
                            text = meditationText,
                            style = TextStyle(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Light,
                                color = Color.White,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 30.sp
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(48.dp))
                        
                        Text(
                            "CLOSE",
                            style = TextStyle(
                                color = Color(0xFFFF69B4),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                fontSize = 14.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
