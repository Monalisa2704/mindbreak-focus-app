package com.mindbreak.mindbreak

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isCompleted: Boolean = false
)

data class BrainTeaser(
    val question: String,
    val answer: String
)

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _isInterruptionActive = MutableStateFlow(false)
    val isInterruptionActive: StateFlow<Boolean> = _isInterruptionActive.asStateFlow()

    private val _intervalMinutes = MutableStateFlow(1f)
    val intervalMinutes: StateFlow<Float> = _intervalMinutes.asStateFlow()

    private var timerJob: Job? = null

    private fun generateDynamicTeaser(): BrainTeaser {
        return when ((1..5).random()) {
            1 -> { val a = (6..15).random(); val b = (6..12).random(); BrainTeaser("What is $a x $b?", (a * b).toString()) }
            2 -> { val x = (2..12).random(); val a = (2..6).random(); val b = (2..20).random(); BrainTeaser("Solve for x: ${a}x + $b = ${(a * x) + b}", x.toString()) }
            3 -> { val percent = listOf(10, 20, 25, 30, 40, 50, 75).random(); val num = (2..20).random() * 10; BrainTeaser("What is $percent% of $num?", ((percent * num) / 100).toString()) }
            4 -> { val a = (10..30).random(); val b = (5..15).random(); val c = (2..5).random(); BrainTeaser("Math trick: $a + $b x $c (Remember BODMAS!)", (a + (b * c)).toString()) }
            else -> { val speed = listOf(40, 50, 60, 70).random(); val time = (2..5).random(); BrainTeaser("Logic: If a car travels at $speed mph, how far does it go in $time hours?", (speed * time).toString()) }
        }
    }

    fun toggleTask(taskId: String) {
        _tasks.value = _tasks.value.map { if (it.id == taskId) it.copy(isCompleted = !it.isCompleted) else it }
    }

    fun addTask(name: String) { _tasks.value = _tasks.value + Task(name = name) }
    fun deleteTask(taskId: String) { _tasks.value = _tasks.value.filter { it.id != taskId } }

    fun updateInterval(minutes: Float) {
        _intervalMinutes.value = minutes
        if (_isInterruptionActive.value) startTimer()
    }

    fun toggleInterruptionMode(active: Boolean) {
        _isInterruptionActive.value = active
        if (active) startTimer() else { timerJob?.cancel(); timerJob = null }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                val delayMs = (_intervalMinutes.value * 60 * 1000).toLong()
                delay(delayMs)

                val teaser = generateDynamicTeaser()
                val context = getApplication<Application>().applicationContext

                // NO MORE NOTIFICATIONS!
                // We force the app to either show the overlay, or show an error.
                if (Settings.canDrawOverlays(context)) {
                    val intent = Intent(context, PuzzleOverlayActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("QUESTION", teaser.question)
                        putExtra("ANSWER", teaser.answer)
                    }
                    context.startActivity(intent)
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "MindBreak failed to interrupt! Permission is missing.", Toast.LENGTH_LONG).show()
                    }
                    // Turn switch off automatically since it failed
                    _isInterruptionActive.value = false
                    timerJob?.cancel()
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFB85CF6),
                    background = Color(0xFF0F172A),
                    surface = Color(0xFF1E293B),
                    primaryContainer = Color(0xFF3B0764),
                    errorContainer = Color(0xFF7F1D1D),
                    onErrorContainer = Color(0xFFFECACA),
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MindBreakApp()
                }
            }
        }
    }
}

class PuzzleOverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This code forces the screen to turn on and bypass the lock screen if needed!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        val question = intent.getStringExtra("QUESTION") ?: "Error loading puzzle"
        val answer = intent.getStringExtra("ANSWER") ?: ""

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFB85CF6),
                    background = Color.Transparent,
                    surface = Color(0xFF1E293B),
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)) // Very dark transparent background
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { /* Block touches to apps underneath! */ },
                    contentAlignment = Alignment.Center
                ) {
                    PuzzleCard(teaser = BrainTeaser(question, answer), onSolved = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MindBreakApp(viewModel: TaskViewModel = viewModel()) {
    val tasks by viewModel.tasks.collectAsState()
    val isInterruptionActive by viewModel.isInterruptionActive.collectAsState()
    val intervalMinutes by viewModel.intervalMinutes.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Tracks if Android is actually letting us draw over other apps
    var hasPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Filled.Add, contentDescription = "Add Task")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Text("MindBreak Focus", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(16.dp))

            // NEW: Massive Permission Warning Card
            AnimatedVisibility(visible = !hasPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Permission Missing! Click the switch below, select MindBreak, and allow Display Over Apps.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            InterruptionControlCard(
                isActive = isInterruptionActive,
                intervalMinutes = intervalMinutes,
                onToggle = { active ->
                    hasPermission = Settings.canDrawOverlays(context)

                    if (active && !hasPermission) {
                        Toast.makeText(context, "Please allow 'Display over other apps'", Toast.LENGTH_LONG).show()
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        context.startActivity(intent)
                    } else {
                        viewModel.toggleInterruptionMode(active)
                    }
                },
                onIntervalChange = { viewModel.updateInterval(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text("My Productive Goals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(12.dp))

            if (tasks.isEmpty()) {
                Text("No goals added yet. Add what you should be doing instead of wasting time!", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(tasks) { task ->
                        TaskCard(task = task, onToggle = { viewModel.toggleTask(task.id) }, onDelete = { viewModel.deleteTask(task.id) })
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTaskDialog(onDismiss = { showAddDialog = false }, onConfirm = { name -> viewModel.addTask(name); showAddDialog = false })
    }
}

@Composable
fun PuzzleCard(teaser: BrainTeaser, onSolved: () -> Unit) {
    var userAnswer by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.padding(24.dp).fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = "Alert", tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Brain Check! \uD83E\uDDE0", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("To reset your focus and exit your scrolling loop, solve this:")
            Spacer(modifier = Modifier.height(16.dp))

            Text(text = teaser.question, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = userAnswer,
                onValueChange = { userAnswer = it; isError = false },
                label = { Text("Your answer") },
                isError = isError,
                supportingText = { if (isError) Text("Incorrect, try again!") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { if (userAnswer.trim().equals(teaser.answer, ignoreCase = true)) onSolved() else isError = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Check Answer")
            }
        }
    }
}

@Composable
fun InterruptionControlCard(isActive: Boolean, intervalMinutes: Float, onToggle: (Boolean) -> Unit, onIntervalChange: (Float) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (isActive) Icons.Filled.NotificationsActive else Icons.Outlined.Notifications, "Interruption Mode")
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Interruption Mode", fontWeight = FontWeight.Bold)
                }
                Switch(checked = isActive, onCheckedChange = onToggle)
            }
            AnimatedVisibility(visible = isActive) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text("Interrupt me every ${intervalMinutes.toInt()} minutes")
                    Slider(value = intervalMinutes, onValueChange = onIntervalChange, valueRange = 1f..60f, steps = 59)
                }
            }
        }
    }
}

@Composable
fun TaskCard(task: Task, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onToggle() }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle, "Toggle completion", tint = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.width(16.dp))
            Text(task.name, fontWeight = FontWeight.SemiBold, textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Goal", fontWeight = FontWeight.Bold) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("What should you be doing?") }, singleLine = true) },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}