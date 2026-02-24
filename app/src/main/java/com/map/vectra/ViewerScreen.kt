package com.map.vectra

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.*

@Composable
fun ViewerScreen(
    points: List<PointData>,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
    onExport: () -> Unit,
    onUpdatePoint: (Int, Double, Double) -> Unit,
    onSaveState: () -> Unit,
    onUndo: () -> Unit,
    canUndo: Boolean
) {
    val currentPoints by rememberUpdatedState(points)
    val currentOnUpdatePoint by rememberUpdatedState(onUpdatePoint)
    val viewModel: VectraViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    val customLines by viewModel.lines
    var show3DView by remember { mutableStateOf(false) }

    // --- State: Manual Camera & Projection ---
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotation by remember { mutableFloatStateOf(0f) }

    var originX by remember { mutableDoubleStateOf(0.0) }
    var originY by remember { mutableDoubleStateOf(0.0) }
    var latScaleFactor by remember { mutableDoubleStateOf(1.0) } // NEW: GPS Aspect Ratio Fix
    var isOriginSet by remember { mutableStateOf(false) }
    var isInitialFitDone by remember { mutableStateOf(false) }

    // Selection & Locks
    var selectedPointIndex by remember { mutableIntStateOf(-1) }
    var selectedLineIndex by remember { mutableIntStateOf(-1) }
    var lockedPointIndex by remember { mutableIntStateOf(-1) } // NEW: Pinned pivot point

    // Area
    var isAreaMode by remember { mutableStateOf(false) }
    val selectedAreaIndices = remember { mutableStateListOf<Int>() }
    var selectedAreaInfo by remember { mutableStateOf<String?>(null) }
    var selectedAreaPos by remember { mutableStateOf<PointData?>(null) }

    var showLines by remember { mutableStateOf(true) }

    // Modes
    var isAddMode by remember { mutableStateOf(false) }
    var isConnectModePending by remember { mutableStateOf(false) }

    // Canvas Control Locks
    var lockScroll by remember { mutableStateOf(false) }
    var lockRotation by remember { mutableStateOf(false) }
    var lockZoom by remember { mutableStateOf(false) }

    val textMeasurer = rememberTextMeasurer()
    val backgroundColor = MaterialTheme.colorScheme.surface
    val dotColor = MaterialTheme.colorScheme.primary
    val selectedColor = Color.Yellow
    val areaSelectedDotColor = Color.Red
    val lineColor = MaterialTheme.colorScheme.secondary
    val customLineColor = MaterialTheme.colorScheme.tertiary
    val areaPreviewLineColor = Color.Red.copy(alpha = 0.5f)

    if (show3DView) {
        ViewScreen(
            points = currentPoints,
            customLines = customLines,
            onBack = { show3DView = false }
        )
        return
    }

    // Mathematical Distance for Google Earth Real Coordinates
    fun earthDistanceMeters(p1: PointData, p2: PointData): Double {
        val earthRadius = 6378137.0
        val lat1 = p1.y * (PI / 180.0)
        val lat2 = p2.y * (PI / 180.0)
        val dLat = (p2.y - p1.y) * (PI / 180.0)
        val dLon = (p2.x - p1.x) * (PI / 180.0)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1.0 - a))
        return earthRadius * c
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        val canvasWidth = constraints.maxWidth.toFloat()
        val canvasHeight = constraints.maxHeight.toFloat()
        val validPoints = remember(currentPoints) { currentPoints.filter { it.x.isFinite() && it.y.isFinite() } }

        // Core Fix: High-Precision Origin & GPS Aspect Ratio
        LaunchedEffect(validPoints) {
            if (!isInitialFitDone && validPoints.isNotEmpty() && canvasWidth > 0) {
                val minX = validPoints.minOf { it.x }
                val maxX = validPoints.maxOf { it.x }
                val minY = validPoints.minOf { it.y }
                val maxY = validPoints.maxOf { it.y }

                if (!isOriginSet) {
                    originX = (minX + maxX) / 2.0
                    originY = (minY + maxY) / 2.0

                    // Detect if data is GPS (Lat/Lon). If so, fix the aspect ratio curvature.
                    val isGPS = validPoints.all { abs(it.x) <= 180.0 && abs(it.y) <= 90.0 }
                    latScaleFactor = if (isGPS) cos(originY * (PI / 180.0)) else 1.0

                    isOriginSet = true
                }

                // Apply the LatScaleFactor so the bounding box fits properly
                val diffX = ((maxX - minX) * latScaleFactor).coerceAtLeast(0.000001)
                val diffY = (maxY - minY).coerceAtLeast(0.000001)

                val scaleX = (canvasWidth * 0.7) / diffX
                val scaleY = (canvasHeight * 0.7) / diffY
                scale = min(scaleX, scaleY).toFloat().coerceIn(0.1f, 1000000000f)

                offset = Offset.Zero
                isInitialFitDone = true
            }
        }

        fun worldToScreen(p: PointData): Offset {
            val dx = (p.x - originX) * latScaleFactor * scale.toDouble()
            val dy = -(p.y - originY) * scale.toDouble()

            val rad = rotation * (PI / 180.0)
            val cosVal = cos(rad)
            val sinVal = sin(rad)

            val rx = dx * cosVal - dy * sinVal
            val ry = dx * sinVal + dy * cosVal

            return Offset(
                (rx + (canvasWidth / 2.0) + offset.x).toFloat(),
                (ry + (canvasHeight / 2.0) + offset.y).toFloat()
            )
        }

        fun screenToWorld(pos: Offset): PointData {
            val rx = pos.x - (canvasWidth / 2f) - offset.x
            val ry = pos.y - (canvasHeight / 2f) - offset.y

            val rad = -rotation * (PI / 180.0)
            val cosVal = cos(rad)
            val sinVal = sin(rad)

            val sx = rx * cosVal - ry * sinVal
            val sy = rx * sinVal + ry * cosVal

            val dx = sx / (scale.toDouble() * latScaleFactor)
            val dy = -(sy / scale.toDouble())

            return PointData(0, dx + originX, dy + originY)
        }

        fun isSafeToDraw(screenPos: Offset): Boolean {
            val buffer = 2000f
            return screenPos.x.isFinite() && screenPos.y.isFinite() &&
                    screenPos.x > -buffer && screenPos.x < canvasWidth + buffer &&
                    screenPos.y > -buffer && screenPos.y < canvasHeight + buffer
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(lockScroll, lockRotation, lockZoom) {
                    detectTransformGestures { centroid, pan, zoom, rotate ->
                        val actualZoom = if (lockZoom) 1f else zoom
                        val actualRotate = if (lockRotation) 0f else rotate
                        val actualPan = if (lockScroll) Offset.Zero else pan

                        val oldScale = scale
                        scale = (scale * actualZoom).coerceIn(0.1f, 1000000000f)
                        val zoomFactor = scale / oldScale

                        rotation += actualRotate

                        val cx = centroid.x - (canvasWidth / 2f)
                        val cy = centroid.y - (canvasHeight / 2f)
                        val dx = (offset.x - cx) * zoomFactor
                        val dy = (offset.y - cy) * zoomFactor

                        val rad = (actualRotate * (PI / 180.0)).toFloat()
                        val cosVal = cos(rad)
                        val sinVal = sin(rad)

                        val rx = dx * cosVal - dy * sinVal
                        val ry = dx * sinVal + dy * cosVal

                        offset = Offset(rx + cx + actualPan.x, ry + cy + actualPan.y)
                    }
                }
                .pointerInput(isAddMode, isConnectModePending, isAreaMode, selectedPointIndex, lockedPointIndex) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val worldPos = screenToWorld(down.position)

                        val hitRadius = (50.0 / scale.toDouble())

                        var hitPointIndex = currentPoints.indexOfFirst {
                            it.x.isFinite() && it.y.isFinite() &&
                                    CsvDxfUtils.distance(it, worldPos) < hitRadius
                        }

                        if (isAreaMode) {
                            down.consume()
                            val up = waitForUpOrCancellation()
                            if (up != null && hitPointIndex != -1) {
                                if (selectedAreaIndices.contains(hitPointIndex)) {
                                    selectedAreaIndices.remove(hitPointIndex)
                                } else {
                                    selectedAreaIndices.add(hitPointIndex)
                                }
                                selectedAreaInfo = null
                            }
                        } else {
                            var hitLineIndex = -1
                            if (hitPointIndex == -1 && showLines && currentPoints.size > 1) {
                                for (i in 0 until currentPoints.size - 1) {
                                    val p1 = currentPoints[i]
                                    val p2 = currentPoints[i + 1]
                                    if (p1.x.isFinite() && p1.y.isFinite() && p2.x.isFinite() && p2.y.isFinite()) {
                                        if (CsvDxfUtils.distanceToSegment(worldPos, p1, p2) < hitRadius) {
                                            hitLineIndex = i
                                            break
                                        }
                                    }
                                }
                            }

                            if (hitPointIndex != -1 || hitLineIndex != -1) {
                                down.consume()

                                if (isConnectModePending && hitPointIndex != -1) {
                                    if (selectedPointIndex != -1 && selectedPointIndex != hitPointIndex) {
                                        viewModel.connectPoints(selectedPointIndex, hitPointIndex)
                                        selectedPointIndex = -1
                                        isConnectModePending = false
                                    }
                                } else {
                                    onSaveState()
                                    if (hitPointIndex != -1) {
                                        selectedPointIndex = hitPointIndex
                                        selectedLineIndex = -1
                                        selectedAreaInfo = null
                                    } else {
                                        selectedLineIndex = hitLineIndex
                                        selectedPointIndex = -1
                                        selectedAreaInfo = null
                                    }
                                    isConnectModePending = false
                                }

                                // Identify all points that need to move (treating overlapped points as a single entity)
                                val indicesToMove = mutableSetOf<Int>()
                                val initialPositions = mutableMapOf<Int, PointData>()

                                if (hitPointIndex != -1) {
                                    val initialSelected = currentPoints[hitPointIndex]
                                    currentPoints.indices.forEach { i ->
                                        val p = currentPoints[i]
                                        if (p.x.isFinite() && p.y.isFinite() &&
                                            abs(p.x - initialSelected.x) < 0.000001 &&
                                            abs(p.y - initialSelected.y) < 0.000001) {
                                            indicesToMove.add(i)
                                            initialPositions[i] = p
                                        }
                                    }
                                } else if (hitLineIndex != -1 && hitLineIndex < currentPoints.size - 1) {
                                    val p1 = currentPoints[hitLineIndex]
                                    val p2 = currentPoints[hitLineIndex + 1]
                                    currentPoints.indices.forEach { i ->
                                        val p = currentPoints[i]
                                        if (p.x.isFinite() && p.y.isFinite()) {
                                            val overlapsP1 = abs(p.x - p1.x) < 0.000001 && abs(p.y - p1.y) < 0.000001
                                            val overlapsP2 = abs(p.x - p2.x) < 0.000001 && abs(p.y - p2.y) < 0.000001
                                            if (overlapsP1 || overlapsP2) {
                                                indicesToMove.add(i)
                                                initialPositions[i] = p
                                            }
                                        }
                                    }
                                }

                                var accumulatedRotDx = 0.0
                                var accumulatedRotDy = 0.0
                                var dragPointerId = down.id

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.find { it.id == dragPointerId }
                                    if (change == null || !change.pressed) break

                                    val dragAmount = change.position - change.previousPosition
                                    if (dragAmount.getDistance() > 0f) {
                                        val rad = (-rotation * (PI / 180.0)).toFloat()
                                        val cosVal = cos(rad)
                                        val sinVal = sin(rad)

                                        val rotDx = (dragAmount.x * cosVal - dragAmount.y * sinVal) / latScaleFactor
                                        val rotDy = -(dragAmount.x * sinVal + dragAmount.y * cosVal)

                                        accumulatedRotDx += rotDx / scale
                                        accumulatedRotDy += rotDy / scale

                                        indicesToMove.forEach { idx ->
                                            if (idx != lockedPointIndex) {
                                                val initialPt = initialPositions[idx]
                                                if (initialPt != null) {
                                                    val newX = initialPt.x + accumulatedRotDx
                                                    val newY = initialPt.y + accumulatedRotDy
                                                    currentOnUpdatePoint(idx, newX, newY)
                                                }
                                            }
                                        }
                                    }
                                    change.consume()
                                }
                            } else {
                                val up = waitForUpOrCancellation()
                                if (up != null) {
                                    if (isAddMode) {
                                        val tapPos = screenToWorld(up.position)
                                        viewModel.addPointIsolated(tapPos.x, tapPos.y)
                                        isAddMode = false
                                        selectedPointIndex = currentPoints.size
                                    } else {
                                        selectedPointIndex = -1
                                        selectedLineIndex = -1
                                        isConnectModePending = false
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            val labelsToDraw = mutableListOf<Pair<Offset, String>>()
            val pointMap = points.associateBy { it.id }
            val isGPS = validPoints.isNotEmpty() && validPoints.all { abs(it.x) <= 180.0 && abs(it.y) <= 90.0 }

            if (isAreaMode && selectedAreaIndices.size > 1) {
                for (i in 0 until selectedAreaIndices.size - 1) {
                    val idx1 = selectedAreaIndices[i]
                    val idx2 = selectedAreaIndices[i+1]
                    if (idx1 < points.size && idx2 < points.size) {
                        drawLine(areaPreviewLineColor, worldToScreen(points[idx1]), worldToScreen(points[idx2]), strokeWidth = 3f)
                    }
                }
                if (selectedAreaIndices.size > 2) {
                    val idxLast = selectedAreaIndices.last()
                    val idxFirst = selectedAreaIndices.first()
                    if (idxLast < points.size && idxFirst < points.size) {
                        drawLine(areaPreviewLineColor, worldToScreen(points[idxLast]), worldToScreen(points[idxFirst]), strokeWidth = 3f)
                    }
                }
            }

            if (showLines) {
                if (points.size > 1) {
                    for (i in 0 until points.size - 1) {
                        val p1 = points[i]
                        val p2 = points[i + 1]
                        if (p1.x.isFinite() && p1.y.isFinite() && p2.x.isFinite() && p2.y.isFinite()) {
                            val isLineSelected = (i == selectedLineIndex)
                            val actualLineColor = if (isLineSelected) selectedColor else lineColor
                            val lineWidth = if (isLineSelected) 8f else 4f
                            val screenP1 = worldToScreen(p1)
                            val screenP2 = worldToScreen(p2)

                            drawLine(color = actualLineColor, start = screenP1, end = screenP2, strokeWidth = lineWidth)

                            if (isLineSelected) {
                                val length = if (isGPS) earthDistanceMeters(p1, p2) else CsvDxfUtils.distance(p1, p2)
                                val unit = if (isGPS) "m" else "units"
                                val midX = (p1.x + p2.x) / 2.0
                                val midY = (p1.y + p2.y) / 2.0
                                labelsToDraw.add(worldToScreen(PointData(0, midX, midY)) to "<-- ${"%.2f".format(length)} $unit -->")
                            }
                        }
                    }
                }

                customLines.forEach { (id1, id2) ->
                    val p1 = pointMap[id1]
                    val p2 = pointMap[id2]
                    if (p1 != null && p2 != null && p1.x.isFinite() && p1.y.isFinite() && p2.x.isFinite() && p2.y.isFinite()) {
                        drawLine(color = customLineColor, start = worldToScreen(p1), end = worldToScreen(p2), strokeWidth = 4f)
                    }
                }
            }

            points.forEachIndexed { index, point ->
                if (point.x.isFinite() && point.y.isFinite()) {
                    val isSelected = index == selectedPointIndex
                    val isAreaSelected = isAreaMode && selectedAreaIndices.contains(index)
                    val isConnectTarget = isConnectModePending && isSelected
                    val isLocked = index == lockedPointIndex // NEW: Identify locked point

                    val radius = if (isSelected || isAreaSelected || isLocked) 15f else 8f

                    val color = if (isConnectTarget) Color.Red
                    else if (isAreaSelected) areaSelectedDotColor
                    else if (isLocked) Color.Magenta // Draw Locked point as highly visible Magenta
                    else if (isSelected) selectedColor
                    else dotColor

                    val screenPos = worldToScreen(point)
                    drawCircle(color = color, radius = radius, center = screenPos)

                    // Draw inner white pin indicator for locked points
                    if (isLocked) {
                        drawCircle(color = Color.White, radius = radius * 0.4f, center = screenPos)
                    }

                    val showCoord = isSelected || (selectedLineIndex != -1 && (index == selectedLineIndex || index == selectedLineIndex + 1))
                    if (showCoord) {
                        labelsToDraw.add(screenPos to "(${String.format(Locale.US, "%.6f", point.x)}, ${String.format(Locale.US, "%.6f", point.y)})")
                    }
                }
            }

            if (selectedAreaInfo != null && selectedAreaPos != null) {
                val pos = selectedAreaPos!!
                if (pos.x.isFinite() && pos.y.isFinite()) {
                    labelsToDraw.add(worldToScreen(pos) to selectedAreaInfo!!)
                }
            }

            labelsToDraw.forEach { (screenPos, text) ->
                if (isSafeToDraw(screenPos)) {
                    try {
                        val xOffset = if (screenPos.x > canvasWidth - 200) -250f else 40f
                        drawText(
                            textMeasurer = textMeasurer,
                            text = text,
                            topLeft = Offset(screenPos.x + xOffset, screenPos.y - 50f),
                            style = TextStyle(color = selectedColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, background = Color.Black.copy(alpha = 0.6f))
                        )
                    } catch (e: Exception) { }
                }
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), RoundedCornerShape(50))
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }

        val isSelectionActive = selectedPointIndex != -1 || selectedLineIndex != -1
        val showConnectOption = selectedPointIndex != -1
        val showLockPointOption = selectedPointIndex != -1 || lockedPointIndex != -1
        val isPointLocked = lockedPointIndex != -1

        IconLayout(
            modifier = Modifier.align(Alignment.BottomEnd),
            isPortrait = isPortrait,
            isSelectionActive = isSelectionActive,
            onDeleteClick = {
                if (selectedPointIndex != -1) {
                    val sp = currentPoints[selectedPointIndex]
                    val overlappingIndices = currentPoints.indices.filter {
                        val p = currentPoints[it]
                        p.x.isFinite() && p.y.isFinite() && abs(p.x - sp.x) < 0.000001 && abs(p.y - sp.y) < 0.000001
                    }
                    overlappingIndices.forEach { idx ->
                        viewModel.deletePointWithGap(idx)
                        if (lockedPointIndex == idx) lockedPointIndex = -1
                    }
                }
                else if (selectedLineIndex != -1) viewModel.breakLine(selectedLineIndex)
                selectedPointIndex = -1
                selectedLineIndex = -1
                selectedAreaInfo = null
            },
            isAddMode = isAddMode,
            onAddModeChange = { isAddMode = it },
            showLines = showLines,
            onShowLinesChange = { showLines = it },
            showConnectOption = showConnectOption,
            isConnectModePending = isConnectModePending,
            onConnectModeChange = { isConnectModePending = it },
            showLockPointOption = showLockPointOption,
            isPointLocked = isPointLocked,
            onLockPointToggle = { isLocked ->
                lockedPointIndex = if (isLocked && selectedPointIndex != -1) selectedPointIndex else -1
            },
            isAreaMode = isAreaMode,
            onAreaModeClick = {
                if (isAreaMode) {
                    val polyPoints = selectedAreaIndices.mapNotNull { idx ->
                        if (idx in points.indices) points[idx] else null
                    }
                    if (polyPoints.size >= 3) {
                        val area = AreaCalculation.calculateArea(polyPoints)
                        val isGPS = polyPoints.all { abs(it.x) <= 180.0 && abs(it.y) <= 90.0 }
                        val unit = if (isGPS) "m²" else "sq units"

                        selectedAreaInfo = "Area: ${"%.2f".format(area)} $unit"

                        var avgX = 0.0
                        var avgY = 0.0
                        polyPoints.forEach { avgX += it.x; avgY += it.y }
                        selectedAreaPos = PointData(0, avgX / polyPoints.size, avgY / polyPoints.size)
                        isAreaMode = false
                        selectedAreaIndices.clear()
                    } else {
                        scope.launch { snackbarHostState.showSnackbar("Select at least 3 points") }
                        isAreaMode = false
                        selectedAreaIndices.clear()
                    }
                } else {
                    isAreaMode = true
                    selectedAreaInfo = null
                    selectedAreaIndices.clear()
                    selectedPointIndex = -1
                    selectedLineIndex = -1
                    isAddMode = false
                    isConnectModePending = false
                }
            },
            lockScroll = lockScroll,
            onLockScrollChange = { lockScroll = it },
            lockRotation = lockRotation,
            onLockRotationChange = { lockRotation = it },
            lockZoom = lockZoom,
            onLockZoomChange = { lockZoom = it },
            onOpenEditor = onOpenEditor,
            canUndo = canUndo,
            onUndo = onUndo,
            onExport = onExport,
            onOpen3DView = { show3DView = true }
        )

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
    }
}