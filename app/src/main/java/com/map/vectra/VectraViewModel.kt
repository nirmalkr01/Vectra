package com.map.vectra

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import kotlin.math.abs

enum class AppStep {
    START,
    MAPPING,
    DUPLICATE_CHECK,
    EDITOR,
    VIEWER
}

data class HistoryState(
    val points: List<PointData>,
    val lines: List<Pair<Int, Int>>
)

data class ProjectSession(
    val id: String,
    var name: String,
    var timestamp: Long,
    var points: List<PointData>,
    var lines: List<Pair<Int, Int>>
)

sealed class DuplicateAction {
    object KeepOne : DuplicateAction()
    object DeleteAll : DuplicateAction()
    object Ignore : DuplicateAction()
    data class Modify(val newX1: Double, val newY1: Double, val newX2: Double, val newY2: Double) : DuplicateAction()
}

data class DuplicateInfo(
    val index1: Int,
    val index2: Int,
    val index3: Int,
    val index4: Int,
    val key: String
)

class VectraViewModel : ViewModel() {

    var isDarkMode = mutableStateOf(true)
    var currentStep = mutableStateOf(AppStep.START)

    var points = mutableStateOf(listOf<PointData>())
        private set

    var lines = mutableStateOf(listOf<Pair<Int, Int>>())

    private val history = mutableListOf<HistoryState>()

    var canUndo = mutableStateOf(false)
        private set

    var rawCsvData = mutableStateOf(listOf<List<String>>())

    var duplicateGroup = mutableStateOf<DuplicateInfo?>(null)
    private val ignoredDuplicates = mutableSetOf<String>()

    val savedSessions = mutableStateListOf<ProjectSession>()
    private var currentSessionId: String? = null
    private var pendingSessionName: String? = null
    private var isLoadedFromDisk = false

    private val PREFS_NAME = "VectraWorkspacePrefs"
    private val KEY_SESSIONS = "saved_sessions_data"

    fun saveSessionsToDisk(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonArray = JSONArray()

            savedSessions.forEach { session ->
                val obj = JSONObject()
                obj.put("id", session.id)
                obj.put("name", session.name)
                obj.put("timestamp", session.timestamp)

                val pArr = JSONArray()
                session.points.forEach { p ->
                    val pObj = JSONObject()
                    pObj.put("id", p.id)
                    if (p.x.isNaN() || p.y.isNaN()) {
                        pObj.put("is_gap", true)
                    } else {
                        pObj.put("is_gap", false)
                        pObj.put("x", p.x)
                        pObj.put("y", p.y)
                    }
                    pArr.put(pObj)
                }
                obj.put("points", pArr)

                val lArr = JSONArray()
                session.lines.forEach { l ->
                    val lObj = JSONObject()
                    lObj.put("first", l.first)
                    lObj.put("second", l.second)
                    lArr.put(lObj)
                }
                obj.put("lines", lArr)

                jsonArray.put(obj)
            }

            prefs.edit().putString(KEY_SESSIONS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadSessionsFromDisk(context: Context) {
        if (isLoadedFromDisk) return

        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_SESSIONS, null)

            if (jsonStr != null) {
                val jsonArray = JSONArray(jsonStr)
                val loadedList = mutableListOf<ProjectSession>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.getString("id")
                    val name = obj.getString("name")
                    val timestamp = obj.getLong("timestamp")

                    val pArr = obj.getJSONArray("points")
                    val loadedPoints = mutableListOf<PointData>()
                    for (j in 0 until pArr.length()) {
                        val pObj = pArr.getJSONObject(j)
                        val pId = pObj.getInt("id")
                        val isGap = pObj.optBoolean("is_gap", false)

                        if (isGap) {
                            loadedPoints.add(PointData(pId, Double.NaN, Double.NaN))
                        } else {
                            loadedPoints.add(PointData(
                                pId,
                                pObj.getDouble("x"),
                                pObj.getDouble("y")
                            ))
                        }
                    }

                    val lArr = obj.getJSONArray("lines")
                    val loadedLines = mutableListOf<Pair<Int, Int>>()
                    for (j in 0 until lArr.length()) {
                        val lObj = lArr.getJSONObject(j)
                        loadedLines.add(Pair(lObj.getInt("first"), lObj.getInt("second")))
                    }

                    loadedList.add(ProjectSession(id, name, timestamp, loadedPoints, loadedLines))
                }

                savedSessions.clear()
                savedSessions.addAll(loadedList)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isLoadedFromDisk = true
    }

    fun forceSaveCurrentState(context: Context) {
        if (currentSessionId != null && currentStep.value == AppStep.VIEWER) {
            val session = savedSessions.find { it.id == currentSessionId }
            if (session != null) {
                session.points = points.value.toList()
                session.lines = lines.value.toList()
                session.timestamp = System.currentTimeMillis()
            }
            saveSessionsToDisk(context)
        }
    }

    fun closeCurrentSession(context: Context) {
        forceSaveCurrentState(context)
        currentSessionId = null
        currentStep.value = AppStep.START
    }

    fun cancelImport() {
        currentSessionId = null
        pendingSessionName = null
        currentStep.value = AppStep.START
    }

    fun openSession(session: ProjectSession, context: Context) {
        session.timestamp = System.currentTimeMillis()

        points.value = session.points.toList()
        lines.value = session.lines.toList()
        history.clear()
        canUndo.value = false

        currentSessionId = session.id
        currentStep.value = AppStep.VIEWER

        val updatedList = savedSessions.toList()
        savedSessions.clear()
        savedSessions.addAll(updatedList)

        saveSessionsToDisk(context)
    }

    fun deleteSession(sessionId: String, context: Context) {
        savedSessions.removeAll { it.id == sessionId }
        saveSessionsToDisk(context)
    }

    // UPDATED: Dynamically checks if it is CSV or DXF
    fun handleImportFile(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                var fileName = "Imported Project"

                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val displayNameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex != -1) fileName = it.getString(displayNameIndex)
                    }
                }
                pendingSessionName = fileName

                val inputStream = contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val fileContent = reader.use { it.readText() }

                if (fileName.endsWith(".dxf", ignoreCase = true)) {
                    // It's a DXF, process and skip mapping phase entirely
                    val (dxfPoints, dxfLines) = CsvDxfUtils.parseDxf(fileContent)

                    launch(Dispatchers.Main) {
                        points.value = dxfPoints
                        lines.value = dxfLines
                        history.clear()
                        canUndo.value = false
                        ignoredDuplicates.clear()

                        // Route through duplicate check directly to ensure overlapped boundaries combine correctly
                        val dup = findDuplicateLine(dxfPoints)
                        if (dup != null) {
                            duplicateGroup.value = dup
                            currentStep.value = AppStep.DUPLICATE_CHECK
                        } else {
                            finalizeImport(context)
                        }
                    }
                } else {
                    // Fallback to standard CSV Map Import
                    val rawRows = CsvDxfUtils.parseRawCsv(fileContent)
                    launch(Dispatchers.Main) {
                        rawCsvData.value = rawRows
                        currentStep.value = AppStep.MAPPING
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun confirmMapping(xIndex: Int, yIndex: Int, context: Context) {
        viewModelScope.launch(Dispatchers.Default) {
            val newPoints = CsvDxfUtils.mapToPoints(rawCsvData.value, xIndex, yIndex)
            points.value = newPoints
            lines.value = emptyList()
            history.clear()
            canUndo.value = false
            ignoredDuplicates.clear()

            val dup = findDuplicateLine(newPoints)
            if (dup != null) {
                duplicateGroup.value = dup
                currentStep.value = AppStep.DUPLICATE_CHECK
            } else {
                finalizeImport(context)
            }
        }
    }

    private fun finalizeImport(context: Context) {
        currentSessionId = UUID.randomUUID().toString()
        val newSession = ProjectSession(
            id = currentSessionId!!,
            name = pendingSessionName ?: "Imported Project",
            timestamp = System.currentTimeMillis(),
            points = points.value.toList(),
            lines = lines.value.toList()
        )

        savedSessions.add(newSession)
        saveSessionsToDisk(context)
        pendingSessionName = null
        currentStep.value = AppStep.VIEWER
    }

    private fun findDuplicateLine(pts: List<PointData>): DuplicateInfo? {
        if (pts.size < 4) return null

        for (i in 0 until pts.size - 1) {
            val p1 = pts[i]
            val p2 = pts[i+1]
            if (p1.x.isNaN() || p2.x.isNaN()) continue

            for (j in i + 1 until pts.size - 1) {
                val p3 = pts[j]
                val p4 = pts[j+1]
                if (p3.x.isNaN() || p4.x.isNaN()) continue

                val exactMatch = (isSame(p1, p3) && isSame(p2, p4))
                val reverseMatch = (isSame(p1, p4) && isSame(p2, p3))

                if (exactMatch || reverseMatch) {
                    val dupKey = setOf(p1.id, p2.id, p3.id, p4.id).toString()
                    if (!ignoredDuplicates.contains(dupKey)) {
                        return DuplicateInfo(i, i+1, j, j+1, dupKey)
                    }
                }
            }
        }
        return null
    }

    private fun isSame(a: PointData, b: PointData): Boolean {
        return abs(a.x - b.x) < 0.0000001 && abs(a.y - b.y) < 0.0000001
    }

    fun resolveDuplicate(action: DuplicateAction, context: Context) {
        val dup = duplicateGroup.value ?: return
        val currentList = points.value.toMutableList()

        val indicesToClear = mutableSetOf<Int>()

        when (action) {
            is DuplicateAction.Ignore -> {
                ignoredDuplicates.add(dup.key)
            }
            is DuplicateAction.KeepOne -> {
                indicesToClear.add(dup.index4)
                if (dup.index3 != dup.index2) {
                    indicesToClear.add(dup.index3)
                }
            }
            is DuplicateAction.DeleteAll -> {
                indicesToClear.addAll(listOf(dup.index1, dup.index2, dup.index4))
                if (dup.index3 != dup.index2) {
                    indicesToClear.add(dup.index3)
                }
            }
            is DuplicateAction.Modify -> {
                val p1 = currentList[dup.index1]
                val p2 = currentList[dup.index2]
                currentList[dup.index1] = p1.copy(x = action.newX1, y = action.newY1)
                currentList[dup.index2] = p2.copy(x = action.newX2, y = action.newY2)

                indicesToClear.add(dup.index4)
                if (dup.index3 != dup.index2) {
                    indicesToClear.add(dup.index3)
                }
            }
        }

        indicesToClear.forEach { index ->
            if (index in currentList.indices) {
                val old = currentList[index]
                currentList[index] = old.copy(x = Double.NaN, y = Double.NaN)
            }
        }

        points.value = currentList

        val nextDup = findDuplicateLine(currentList)
        if (nextDup != null) {
            duplicateGroup.value = nextDup
        } else {
            duplicateGroup.value = null
            finalizeImport(context)
        }
    }

    fun saveDxfFile(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dxfContent = CsvDxfUtils.generateDxf(points.value, lines.value)
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(dxfContent.toByteArray())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getPolygons(): List<List<PointData>> {
        val result = mutableListOf<List<PointData>>()
        var currentPoly = mutableListOf<PointData>()

        points.value.forEach { p ->
            if (p.x.isNaN() || p.y.isNaN()) {
                if (currentPoly.isNotEmpty()) {
                    result.add(currentPoly)
                    currentPoly = mutableListOf()
                }
            } else {
                currentPoly.add(p)
            }
        }
        if (currentPoly.isNotEmpty()) {
            result.add(currentPoly)
        }
        return result
    }

    fun saveStateForUndo() {
        if (history.size > 50) history.removeAt(0)
        history.add(HistoryState(points.value.toList(), lines.value.toList()))
        canUndo.value = true
    }

    fun undo() {
        if (history.isNotEmpty()) {
            val state = history.removeAt(history.lastIndex)
            points.value = state.points
            lines.value = state.lines
            canUndo.value = history.isNotEmpty()
        }
    }

    fun updatePoint(index: Int, newX: Double, newY: Double, saveHistory: Boolean = false) {
        if (saveHistory) saveStateForUndo()
        val currentList = points.value.toMutableList()
        if (index in currentList.indices) {
            val origX = currentList[index].x
            val origY = currentList[index].y

            for (i in currentList.indices) {
                val p = currentList[i]
                if (p.x.isFinite() && p.y.isFinite() && abs(p.x - origX) < 0.000001 && abs(p.y - origY) < 0.000001) {
                    currentList[i] = p.copy(x = newX, y = newY)
                }
            }
            points.value = currentList
        }
    }

    fun updateLine(index1: Int, newX1: Double, newY1: Double, index2: Int, newX2: Double, newY2: Double, saveHistory: Boolean = false) {
        if (saveHistory) saveStateForUndo()
        val currentList = points.value.toMutableList()

        val origX1 = currentList.getOrNull(index1)?.x
        val origY1 = currentList.getOrNull(index1)?.y
        val origX2 = currentList.getOrNull(index2)?.x
        val origY2 = currentList.getOrNull(index2)?.y

        if (origX1 != null && origY1 != null) {
            for (i in currentList.indices) {
                val p = currentList[i]
                if (p.x.isFinite() && p.y.isFinite() && abs(p.x - origX1) < 0.000001 && abs(p.y - origY1) < 0.000001) {
                    currentList[i] = p.copy(x = newX1, y = newY1)
                }
            }
        }

        if (origX2 != null && origY2 != null) {
            for (i in currentList.indices) {
                val p = currentList[i]
                if (abs(origX1 ?: Double.MAX_VALUE - origX2) < 0.000001 && abs(origY1 ?: Double.MAX_VALUE - origY2) < 0.000001) {
                    continue
                }

                if (p.x.isFinite() && p.y.isFinite() && abs(p.x - origX2) < 0.000001 && abs(p.y - origY2) < 0.000001) {
                    currentList[i] = p.copy(x = newX2, y = newY2)
                }
            }
        }

        points.value = currentList
    }

    fun deletePoint(index: Int) {
        saveStateForUndo()
        val currentList = points.value.toMutableList()
        if (index in currentList.indices) {
            val pointId = currentList[index].id
            val origX = currentList[index].x
            val origY = currentList[index].y

            val idsToRemove = mutableSetOf<Int>()
            val indicesToRemove = mutableListOf<Int>()

            for (i in currentList.indices) {
                val p = currentList[i]
                if (p.x.isFinite() && p.y.isFinite() && abs(p.x - origX) < 0.000001 && abs(p.y - origY) < 0.000001) {
                    indicesToRemove.add(i)
                    idsToRemove.add(p.id)
                }
            }

            if (indicesToRemove.isEmpty()) {
                currentList.removeAt(index)
                lines.value = lines.value.filter { it.first != pointId && it.second != pointId }
            } else {
                indicesToRemove.sortedDescending().forEach { i ->
                    currentList.removeAt(i)
                }
                lines.value = lines.value.filter { it.first !in idsToRemove && it.second !in idsToRemove }
            }
            points.value = currentList
        }
    }

    fun deletePointWithGap(index: Int) {
        saveStateForUndo()
        val currentList = points.value.toMutableList()
        if (index in currentList.indices) {
            val origX = currentList[index].x
            val origY = currentList[index].y

            for (i in currentList.indices) {
                val p = currentList[i]
                if (p.x.isFinite() && p.y.isFinite() && abs(p.x - origX) < 0.000001 && abs(p.y - origY) < 0.000001) {
                    currentList[i] = p.copy(x = Double.NaN, y = Double.NaN)
                }
            }
            points.value = currentList
        }
    }

    fun breakLine(index: Int) {
        saveStateForUndo()
        val currentList = points.value.toMutableList()
        val newId = (currentList.maxOfOrNull { it.id } ?: 0) + 1
        currentList.add(index + 1, PointData(newId, Double.NaN, Double.NaN))
        points.value = currentList
    }

    fun addPointIsolated(x: Double, y: Double) {
        saveStateForUndo()
        val currentList = points.value.toMutableList()
        val newId = (currentList.maxOfOrNull { it.id } ?: 0) + 1

        if (currentList.isNotEmpty() && !currentList.last().x.isNaN()) {
            currentList.add(PointData(newId + 999, Double.NaN, Double.NaN))
        }

        currentList.add(PointData(newId, x, y))
        points.value = currentList
    }

    fun connectPoints(fromIndex: Int, toIndex: Int) {
        saveStateForUndo()
        val currentList = points.value

        if (fromIndex in currentList.indices && toIndex in currentList.indices) {
            val id1 = currentList[fromIndex].id
            val id2 = currentList[toIndex].id
            if (id1 == id2) return

            val currentLines = lines.value.toMutableList()
            val exists = currentLines.any { (a, b) ->
                (a == id1 && b == id2) || (a == id2 && b == id1)
            }

            if (!exists) {
                currentLines.add(id1 to id2)
                lines.value = currentLines
            }
        }
    }

    fun toggleTheme() {
        isDarkMode.value = !isDarkMode.value
    }
}