package com.map.vectra

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val viewModel: VectraViewModel = viewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val step = viewModel.currentStep.value

    // State for the Delete Confirmation Dialog
    var sessionToDelete by remember { mutableStateOf<ProjectSession?>(null) }

    // Load data from disk ONCE when the screen is created
    LaunchedEffect(Unit) {
        viewModel.loadSessionsFromDisk(context)
    }

    // Auto-Save: Catch app pausing or stopping (swiped away) to safely persist changes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_PAUSE) {
                viewModel.forceSaveCurrentState(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Handle Hardware Back Button
    BackHandler(enabled = step != AppStep.START) {
        when (step) {
            AppStep.VIEWER -> viewModel.closeCurrentSession(context) // Preserves state and exits
            AppStep.EDITOR -> viewModel.currentStep.value = AppStep.VIEWER
            AppStep.MAPPING -> viewModel.cancelImport() // User backed out of mapping
            AppStep.DUPLICATE_CHECK -> viewModel.cancelImport() // User backed out of dup check
            else -> { }
        }
    }

    val openCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.previewCsvFile(it, context) }
    }

    val createDxfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/vnd.dxf")
    ) { uri ->
        uri?.let { viewModel.saveDxfFile(it, context.contentResolver) }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        when (step) {
            AppStep.START -> {
                // Determine Greeting based on current time
                val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
                val greeting = when (currentHour) {
                    in 0..11 -> "Good morning"
                    in 12..16 -> "Good afternoon"
                    else -> "Good evening"
                }

                // Filter History
                val now = System.currentTimeMillis()
                val oneDayMillis = 24 * 60 * 60 * 1000L
                val recentProjects = viewModel.savedSessions.filter { now - it.timestamp < oneDayMillis }.sortedByDescending { it.timestamp }
                val olderProjects = viewModel.savedSessions.filter { now - it.timestamp >= oneDayMillis }.sortedByDescending { it.timestamp }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = greeting,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            actions = {
                                IconButton(onClick = { viewModel.toggleTheme() }) {
                                    Text(
                                        text = if (viewModel.isDarkMode.value) "☀️" else "🌙",
                                        fontSize = 24.sp
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                        )
                    },
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            onClick = { openCsvLauncher.launch("text/*") },
                            icon = { Icon(Icons.Default.Add, contentDescription = "New Project") },
                            text = { Text("New", fontWeight = FontWeight.Bold) },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            elevation = FloatingActionButtonDefaults.elevation(8.dp)
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "VECTRA WORKSPACE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        if (viewModel.savedSessions.isEmpty()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    "No recent projects. Click + New to start.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB
                            ) {
                                if (recentProjects.isNotEmpty()) {
                                    item {
                                        Text("Recent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    items(recentProjects) { session ->
                                        ProjectCard(
                                            session = session,
                                            onClick = { viewModel.openSession(it, context) },
                                            onDelete = { sessionToDelete = it }
                                        )
                                    }
                                    item { Spacer(modifier = Modifier.height(16.dp)) }
                                }

                                if (olderProjects.isNotEmpty()) {
                                    item {
                                        Text("Older", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    items(olderProjects) { session ->
                                        ProjectCard(
                                            session = session,
                                            onClick = { viewModel.openSession(it, context) },
                                            onDelete = { sessionToDelete = it }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Delete Confirmation Dialog
                if (sessionToDelete != null) {
                    AlertDialog(
                        onDismissRequest = { sessionToDelete = null },
                        title = { Text("Delete Project") },
                        text = { Text("Are you sure you want to delete '${sessionToDelete?.name}'? You will not be able to restore it.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    sessionToDelete?.let { viewModel.deleteSession(it.id, context) }
                                    sessionToDelete = null
                                }
                            ) {
                                Text("OK", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { sessionToDelete = null }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AppStep.MAPPING -> {
                MappingScreen(
                    rawData = viewModel.rawCsvData.value,
                    onConfirm = { x, y -> viewModel.confirmMapping(x, y, context) },
                    onCancel = { viewModel.cancelImport() }
                )
            }

            AppStep.DUPLICATE_CHECK -> {
                DuplicateCheckScreen(
                    duplicateInfo = viewModel.duplicateGroup.value,
                    points = viewModel.points.value,
                    onResolve = { action -> viewModel.resolveDuplicate(action, context) }
                )
            }

            AppStep.EDITOR -> {
                EditorScreen(
                    points = viewModel.points.value,
                    onUpdatePoint = { i, x, y -> viewModel.updatePoint(i, x, y, true) },
                    onDeletePoint = { i -> viewModel.deletePoint(i) },
                    onNavigateToView = { viewModel.currentStep.value = AppStep.VIEWER }
                )
            }

            AppStep.VIEWER -> {
                ViewerScreen(
                    points = viewModel.points.value,
                    onBack = { viewModel.closeCurrentSession(context) }, // Preserves data and goes to START
                    onOpenEditor = { viewModel.currentStep.value = AppStep.EDITOR },
                    onExport = { createDxfLauncher.launch("export.dxf") },
                    onUpdatePoint = { i, x, y -> viewModel.updatePoint(i, x, y, false) },
                    onSaveState = { viewModel.saveStateForUndo() },
                    onUndo = { viewModel.undo() },
                    canUndo = viewModel.canUndo.value
                )
            }
        }
    }
}

@Composable
fun ProjectCard(session: ProjectSession, onClick: (ProjectSession) -> Unit, onDelete: (ProjectSession) -> Unit) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
    val dateString = dateFormat.format(Date(session.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick(session) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Project",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { onDelete(session) }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Project",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
        }
    }
}