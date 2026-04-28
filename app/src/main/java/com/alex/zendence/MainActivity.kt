@file:OptIn(ExperimentalMaterial3Api::class)
package com.alex.zendence

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alex.zendence.ui.components.*
import com.alex.zendence.ui.theme.ZendenceTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

class MeditationViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val repository = (application as ZendenceApp).repository

    val history = repository.history
    val presets = repository.presets
    
    var dailyQuote by mutableStateOf("Loading wisdom...")
    
    var isRunning by mutableStateOf(false)
    var initialDurationSec by mutableIntStateOf(repository.getInitialDuration())
    var timeLeftSec by mutableIntStateOf(repository.getInitialDuration())
    var volume by mutableFloatStateOf(repository.getVolume())

    var startingBellEnabled by mutableStateOf(repository.getStartingBellEnabled())
    var startingBellVolume by mutableFloatStateOf(repository.getStartingBellVolume())
    var startingBellUri by mutableStateOf(repository.getStartingBellUri() ?: "")
    var initialSilenceSec by mutableIntStateOf(repository.getInitialSilence())
    var backgroundSoundUri by mutableStateOf(repository.getBackgroundSoundUri() ?: "")
    var meditationReading by mutableStateOf("")
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
        // No longer observeSettings() here as we want to push changes TO repository
        // instead of reacting to repository changes in a loop.
        // We will use snapshotFlow to auto-save.
        setupAutoSave()
        Intent(application, MeditationService::class.java).also { intent ->
            application.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun setupAutoSave() {
        viewModelScope.launch {
            // Monitor all setting states and save on change
            snapshotFlow {
                listOf(
                    initialDurationSec.toString(),
                    volume.toString(),
                    startingBellEnabled.toString(),
                    startingBellVolume.toString(),
                    startingBellUri,
                    initialSilenceSec.toString(),
                    backgroundSoundUri,
                    intervalBells.toList().toString() // Catch bell changes
                )
            }.collectLatest {
                saveCurrentSettings()
                // Also update the active preset if there is one
                updateActivePresetIfAny(this)
            }
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
                    val meditation = repository.getMeditationById(meditationId.toInt())
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
        val bellsJson = repository.getIntervalBellsJson()
        if (!bellsJson.isNullOrEmpty()) {
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
        
        val savedReading = repository.getMeditationReading()
        if (savedReading != null) {
            meditationReading = savedReading
        } else {
            // Load from resource initially
            try {
                getApplication<android.app.Application>().resources.openRawResource(R.raw.meditation).bufferedReader().use {
                    meditationReading = it.readText()
                }
            } catch (e: Exception) {
                meditationReading = "Let me clear my mind..."
            }
        }
    }

    fun updateMeditationReading(text: String) {
        meditationReading = text
        repository.saveMeditationReading(text)
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
        val bellsJson = Json.encodeToString(intervalBells.toList())
        repository.saveSettings(
            duration = initialDurationSec,
            volume = volume,
            startBell = startingBellEnabled,
            startBellVol = startingBellVolume,
            startBellUri = startingBellUri,
            bellsJson = bellsJson,
            silenceSec = initialSilenceSec
        )
        repository.saveBackgroundSoundUri(backgroundSoundUri)
    }

    private fun loadIntervalBellsFromJson(json: String) {
        intervalBells.clear()
        try {
            val bells = Json.decodeFromString<List<IntervalBell>>(json)
            intervalBells.addAll(bells)
        } catch (e: Exception) {
            // Fallback for old format
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
                bgVolume = volume,
                silenceSec = initialSilenceSec,
                bgSoundUri = backgroundSoundUri,
                startBellUri = startingBellUri
            )
        }
    }

    fun savePreset(name: String, scope: CoroutineScope) {
        val bellsJson = Json.encodeToString(
            intervalBells.filter { it.atSecFromStart < initialDurationSec }
        )
        val preset = Preset(
            name = name,
            durationMin = initialDurationSec / 60,
            volume = volume,
            intervalBellsJson = bellsJson,
            backgroundSoundUri = backgroundSoundUri,
            startingBellUri = startingBellUri,
            initialSilenceSec = initialSilenceSec
        )
        scope.launch {
            repository.insertPreset(preset)
        }
    }

    fun loadPreset(preset: Preset) {
        if (!isRunning) {
            activePresetName = preset.name
            initialDurationSec = preset.durationMin * 60
            timeLeftSec = initialDurationSec
            volume = preset.volume
            preset.backgroundSoundUri?.let { backgroundSoundUri = it }
            preset.startingBellUri?.let { startingBellUri = it }
            preset.initialSilenceSec?.let { initialSilenceSec = it }
            loadIntervalBellsFromJson(preset.intervalBellsJson)
        }
    }

    fun updateActivePresetIfAny(scope: CoroutineScope) {
        val currentName = activePresetName ?: return
        val bellsJson = Json.encodeToString(
            intervalBells.filter { it.atSecFromStart < initialDurationSec }
        )
        val preset = Preset(
            name = currentName,
            durationMin = initialDurationSec / 60,
            volume = volume,
            intervalBellsJson = bellsJson,
            backgroundSoundUri = backgroundSoundUri,
            startingBellUri = startingBellUri,
            initialSilenceSec = initialSilenceSec
        )
        scope.launch {
            repository.insertPreset(preset)
        }
    }

    fun deletePreset(preset: Preset, scope: CoroutineScope) {
        scope.launch {
            repository.deletePreset(preset)
        }
    }

    fun resetToDefaults() {
        if (!isRunning) {
            initialDurationSec = 2700
            timeLeftSec = 2700
            volume = 0.5f
            startingBellEnabled = true
            startingBellVolume = 0.7f
            initialSilenceSec = 30
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

    fun onInsightSubmitted(insight: String) {
        val meditation = editingMeditation
        viewModelScope.launch(Dispatchers.IO) {
            meditation?.let {
                val updated = it.copy(insight = insight.ifBlank { null })
                repository.updateMeditation(updated)
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
            repository.deleteMeditation(meditation)
        }
    }

    fun clearHistory(scope: CoroutineScope) {
        scope.launch {
            repository.clearHistory()
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

    fun exportConfig(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentPresets = repository.presets.first()
                val currentHistory = repository.history.first()
                val config = ZendenceConfig(
                    initialDurationSec = initialDurationSec,
                    volume = volume,
                    startingBellEnabled = startingBellEnabled,
                    startingBellVolume = startingBellVolume,
                    startingBellUri = startingBellUri,
                    initialSilenceSec = initialSilenceSec,
                    backgroundSoundUri = backgroundSoundUri,
                    meditationReading = meditationReading,
                    intervalBells = intervalBells.toList(),
                    presets = currentPresets,
                    history = currentHistory
                )
                val json = Json.encodeToString(config)
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(json.toByteArray())
                }
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Settings exported", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Export failed", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun importConfig(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (json != null) {
                    val config = Json.decodeFromString<ZendenceConfig>(json)
                    withContext(Dispatchers.Main) {
                        initialDurationSec = config.initialDurationSec
                        volume = config.volume
                        startingBellEnabled = config.startingBellEnabled
                        startingBellVolume = config.startingBellVolume
                        startingBellUri = config.startingBellUri
                        initialSilenceSec = config.initialSilenceSec
                        backgroundSoundUri = config.backgroundSoundUri
                        meditationReading = config.meditationReading
                        intervalBells.clear()
                        intervalBells.addAll(config.intervalBells)

                        saveCurrentSettings()
                        repository.saveMeditationReading(config.meditationReading)
                        
                        // Handle reading update if it's currently showing
                        updateMeditationReading(config.meditationReading)
                    }
                    config.presets.forEach { repository.insertPreset(it) }
                    config.history.forEach { repository.insertMeditation(it) }
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Settings imported", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Import failed", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

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
    var showBackgroundSoundDialog by remember { mutableStateOf(false) }
    var showStartingBellSoundDialog by remember { mutableStateOf(false) }
    
    var isEditingDuration by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf("") }
    var isBellsExpanded by remember { mutableStateOf(false) }
    
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { vm.exportConfig(context, it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { vm.importConfig(context, it) }
    }

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
                    TimerDisplay(
                        vm = vm,
                        isEditingDuration = isEditingDuration,
                        editValue = editValue,
                        onEditValueChange = { editValue = it },
                        onEditDurationToggle = { isEditingDuration = it },
                        onShowFullReading = { showFullReading = true }
                    )
                }

                item {
                    SessionPresetsSection(
                        vm = vm,
                        presets = presets,
                        onSavePresetClick = { showSavePresetDialog = true },
                        onEditPresetClick = { preset ->
                            presetToEdit = preset
                            vm.loadPreset(preset)
                            showSettings = true
                            isBellsExpanded = true
                            showSavePresetDialog = true
                        },
                        onDeletePresetClick = { presetToDelete = it }
                    )
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SettingsAndHistoryHeader(
                            showSettings = showSettings,
                            onSettingsToggle = { showSettings = !showSettings },
                            showHistory = showHistory,
                            onHistoryToggle = { showHistory = !showHistory }
                        )

                        if (showSettings) {
                            SettingsSection(
                                vm = vm,
                                isBellsExpanded = isBellsExpanded,
                                onBellsExpandedToggle = { isBellsExpanded = !isBellsExpanded },
                                onAddBellClick = { showIntervalDialog = true },
                                onEditBellClick = { bell ->
                                    editingBell = bell
                                    showIntervalDialog = true
                                },
                                onRemoveBellClick = { bell ->
                                    vm.intervalBells.remove(bell)
                                    vm.updateActivePresetIfAny(scope)
                                },
                                onBackgroundSoundClick = { showBackgroundSoundDialog = true },
                                onStartingBellSoundClick = { showStartingBellSoundDialog = true },
                                onExportClick = { exportLauncher.launch("zendence_config.json") },
                                onImportClick = { importLauncher.launch(arrayOf("application/json")) }
                            )
                        }

                        if (showHistory) {
                            HistorySection(
                                history = history,
                                onExportClick = { vm.exportToObsidian(context, history) },
                                onClearHistoryClick = { showClearHistoryDialog = true },
                                onEditInsightClick = { vm.onEditInsight(it) },
                                onDeleteMeditationClick = { vm.deleteMeditation(it, scope) }
                            )
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
            IntervalBellDialog(
                editingBell = editingBell,
                onDismiss = {
                    showIntervalDialog = false
                    editingBell = null
                },
                onConfirm = { totalSec, repeats, type, bellVol, customUri ->
                    if (totalSec > 0) {
                        val newBell = IntervalBell(
                            id = editingBell?.id ?: UUID.randomUUID().toString(),
                            atSecFromStart = totalSec,
                            soundType = type,
                            repeats = repeats,
                            volume = bellVol,
                            soundUri = customUri
                        )
                        editingBell?.let { old ->
                            val index = vm.intervalBells.indexOfFirst { it.id == old.id }
                            if (index != -1) vm.intervalBells[index] = newBell
                        } ?: run {
                            vm.intervalBells.add(newBell)
                        }
                        vm.intervalBells.sortBy { it.atSecFromStart }
                        vm.updateActivePresetIfAny(scope)
                    }
                    showIntervalDialog = false
                    editingBell = null
                }
            )
        }

        if (showSavePresetDialog) {
            SavePresetDialog(
                presetToEdit = presetToEdit,
                onDismiss = {
                    showSavePresetDialog = false
                    presetToEdit = null
                },
                onConfirm = { name ->
                    vm.savePreset(name, scope)
                    showSavePresetDialog = false
                    presetToEdit = null
                }
            )
        }

        if (showBackgroundSoundDialog) {
            SoundSourceDialog(
                title = "Background Sound",
                initialUri = vm.backgroundSoundUri,
                onDismiss = { showBackgroundSoundDialog = false },
                onConfirm = { uri ->
                    vm.backgroundSoundUri = uri
                    showBackgroundSoundDialog = false
                }
            )
        }

        if (showStartingBellSoundDialog) {
            SoundSourceDialog(
                title = "Starting Bell Sound",
                initialUri = vm.startingBellUri,
                onDismiss = { showStartingBellSoundDialog = false },
                onConfirm = { uri ->
                    vm.startingBellUri = uri
                    showStartingBellSoundDialog = false
                }
            )
        }

        if (presetToDelete != null) {
            DeletePresetDialog(
                presetName = presetToDelete?.name ?: "",
                onDismiss = { presetToDelete = null },
                onConfirm = {
                    presetToDelete?.let { vm.deletePreset(it, scope) }
                    presetToDelete = null
                }
            )
        }

        if (showClearHistoryDialog) {
            ClearHistoryDialog(
                onDismiss = { showClearHistoryDialog = false },
                onConfirm = {
                    vm.clearHistory(scope)
                    showClearHistoryDialog = false
                }
            )
        }

        if (vm.showInsightDialog) {
            InsightDialog(
                vm = vm,
                onDismiss = { vm.onInsightDismissed() },
                onConfirm = { insight ->
                    vm.onInsightSubmitted(insight)
                }
            )
        }

        if (showFullReading) {
            FullReadingDialog(
                readingText = vm.meditationReading,
                onSave = { vm.updateMeditationReading(it) },
                onDismiss = { showFullReading = false }
            )
        }
    }
}
