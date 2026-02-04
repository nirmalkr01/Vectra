package com.map.vectra

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

enum class AppStep {
    START,      // Initial "+ Load" screen
    MAPPING,    // Select X and Y columns
    EDITOR,     // The list view (accessed via Viewer)
    VIEWER      // The graph view (Main Screen)
}

class VectraViewModel : ViewModel() {

    // --- STATE ---
    var isDarkMode = mutableStateOf(true)
    var currentStep = mutableStateOf(AppStep.START)
    var points = mutableStateOf(listOf<PointData>())
        private set

    // History for Undo
    private val history = mutableListOf<List<PointData>>()

    // Undo Availability
    var canUndo = mutableStateOf(false)
        private set

    var rawCsvData = mutableStateOf(listOf<List<String>>())

    // --- ACTIONS ---

    fun previewCsvFile(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val csvContent = reader.use { it.readText() }
                val rawRows = CsvDxfUtils.parseRawCsv(csvContent)
                rawCsvData.value = rawRows
                currentStep.value = AppStep.MAPPING
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun confirmMapping(xIndex: Int, yIndex: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            val newPoints = CsvDxfUtils.mapToPoints(rawCsvData.value, xIndex, yIndex)
            points.value = newPoints
            history.clear()
            canUndo.value = false
            // WORKFLOW CHANGE: Go directly to Viewer
            currentStep.value = AppStep.VIEWER
        }
    }

    fun saveDxfFile(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dxfContent = CsvDxfUtils.generateDxf(points.value)
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(dxfContent.toByteArray())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- EDITING & UNDO LOGIC ---

    fun saveStateForUndo() {
        if (history.size > 50) history.removeAt(0)
        history.add(points.value.toList())
        canUndo.value = true
    }

    fun undo() {
        if (history.isNotEmpty()) {
            points.value = history.removeAt(history.lastIndex)
            canUndo.value = history.isNotEmpty()
        }
    }

    fun updatePoint(index: Int, newX: Float, newY: Float, saveHistory: Boolean = false) {
        if (saveHistory) saveStateForUndo()
        val currentList = points.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(x = newX, y = newY)
            points.value = currentList
        }
    }

    fun deletePoint(index: Int) {
        saveStateForUndo()
        val currentList = points.value.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            points.value = currentList
        }
    }

    fun toggleTheme() {
        isDarkMode.value = !isDarkMode.value
    }
}