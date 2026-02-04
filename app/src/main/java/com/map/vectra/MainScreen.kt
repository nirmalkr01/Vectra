package com.map.vectra

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val viewModel: VectraViewModel = viewModel()
    val context = LocalContext.current
    val step = viewModel.currentStep.value

    // Handle Hardware Back Button
    BackHandler(enabled = step != AppStep.START) {
        when (step) {
            AppStep.VIEWER -> viewModel.currentStep.value = AppStep.START // Back exits file
            AppStep.EDITOR -> viewModel.currentStep.value = AppStep.VIEWER // Back goes to Graph
            AppStep.MAPPING -> viewModel.currentStep.value = AppStep.START
            else -> { }
        }
    }

    val openCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.previewCsvFile(it, context.contentResolver) }
    }

    val createDxfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/vnd.dxf")
    ) { uri ->
        uri?.let { viewModel.saveDxfFile(it, context.contentResolver) }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        when (step) {
            AppStep.START -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("VECTRA", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(onClick = { openCsvLauncher.launch("text/*") }, modifier = Modifier.size(200.dp, 60.dp)) {
                            Text("+ Load CSV", fontSize = 18.sp)
                        }
                    }
                    IconButton(onClick = { viewModel.toggleTheme() }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                        Text(if (viewModel.isDarkMode.value) "☀️" else "🌙", fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }

            AppStep.MAPPING -> {
                MappingScreen(
                    rawData = viewModel.rawCsvData.value,
                    onConfirm = { x, y -> viewModel.confirmMapping(x, y) },
                    onCancel = { viewModel.currentStep.value = AppStep.START }
                )
            }

            AppStep.EDITOR -> {
                EditorScreen(
                    points = viewModel.points.value,
                    onUpdatePoint = { i, x, y -> viewModel.updatePoint(i, x, y, true) },
                    onDeletePoint = { i -> viewModel.deletePoint(i) },
                    onNavigateToView = { viewModel.currentStep.value = AppStep.VIEWER },
                    onExport = { createDxfLauncher.launch("export.dxf") }
                )
            }

            AppStep.VIEWER -> {
                ViewerScreen(
                    points = viewModel.points.value,
                    onBack = { viewModel.currentStep.value = AppStep.START }, // Top right back button
                    onOpenEditor = { viewModel.currentStep.value = AppStep.EDITOR },
                    onExport = { createDxfLauncher.launch("export.dxf") },
                    onUpdatePoint = { i, x, y -> viewModel.updatePoint(i, x, y, false) }, // False = don't save every micro-drag to history
                    onSaveState = { viewModel.saveStateForUndo() }, // Save only when drag starts
                    onUndo = { viewModel.undo() },
                    canUndo = viewModel.canUndo.value
                )
            }
        }
    }
}