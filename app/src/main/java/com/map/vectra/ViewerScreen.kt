package com.map.vectra

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
    var calculatedBaseArea by remember { mutableStateOf<Double?>(null) }
    var isGpsArea by remember { mutableStateOf(false) }
    var selectedAreaUnit by remember { mutableIntStateOf(0) } // 0:m2, 1:ha, 2:ft2, 3:ac
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

    // Generate accurate Area Label dynamically based on Selected Unit
    val displayAreaText = if (calculatedBaseArea != null) {
        if (isGpsArea) {
            val converted = when (selectedAreaUnit) {
                0 -> calculatedBaseArea!!
                1 -> calculatedBaseArea!! / 10000.0
                2 -> calculatedBaseArea!! * 10.76391042
                3 -> calculatedBaseArea!! / 4046.856422
                else -> calculatedBaseArea!!
            }
            val unitStr = when (selectedAreaUnit) {
                0 -> "m²"
                1 -> "Hectares"
                2 -> "ft²"
                3 -> "Acres"
                else -> ""
            }
            // Formatted Area to 3 decimal places
            "Area: ${String.format(Locale.US, "%.3f", converted)} $unitStr"
        } else {
            // Formatted Area to 3 decimal places
            "Area: ${String.format(Locale.US, "%.3f", calculatedBaseArea!!)} sq units"
        }
    } else null


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

            // Undo scaling and GPS curve correction, and invert Y back
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
                        // Force requirement of unconsumed touches to respect Area Box UI
                        val down = awaitFirstDown(requireUnconsumed = true)

                        // Hide Area Overlay if user clicks exactly on the map
                        if (calculatedBaseArea != null) {
                            calculatedBaseArea = null
                            selectedAreaPos = null
                        }

                        val worldPos = screenToWorld(down.position)
                        val hitRadius = (50.0 / scale.toDouble())

                        var hitPointIndex = currentPoints.indexOfFirst {
                            it.x.isFinite() && it.y.isFinite() && CsvDxfUtils.distance(it, worldPos) < hitRadius
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
                                calculatedBaseArea = null
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
                                        calculatedBaseArea = null
                                    } else {
                                        selectedLineIndex = hitLineIndex
                                        selectedPointIndex = -1
                                        calculatedBaseArea = null
                                    }
                                    isConnectModePending = false
                                }

                                // 1. SNAPSHOT STATE: Grab identical locked coordinates at exact moment of tap
                                val initialPositions = currentPoints.map { it.copy() }
                                val lockedP = initialPositions.getOrNull(lockedPointIndex)

                                var dragPointerId = down.id
                                var totalRotDx = 0.0
                                var totalRotDy = 0.0

                                // 2. DRAG LOOP
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.find { it.id == dragPointerId }
                                    if (change == null || !change.pressed) break

                                    val dragAmount = change.position - change.previousPosition
                                    if (dragAmount.getDistance() > 0f) {
                                        val rad = (-rotation * (PI / 180.0)).toFloat()
                                        val cosVal = cos(rad)
                                        val sinVal = sin(rad)

                                        // Keep accumulating absolute translation independently of composition refresh
                                        totalRotDx += (dragAmount.x * cosVal - dragAmount.y * sinVal) / latScaleFactor
                                        totalRotDy += -(dragAmount.x * sinVal + dragAmount.y * cosVal)

                                        if (hitPointIndex != -1) {
                                            val initialP = initialPositions[hitPointIndex]
                                            val isLocked = lockedP != null && abs(initialP.x - lockedP.x) < 0.000001 && abs(initialP.y - lockedP.y) < 0.000001

                                            // Apply absolute translation to Viewmodel.
                                            // The ViewModel will now handle auto-syncing the overlapping counterparts safely.
                                            if (!isLocked) {
                                                currentOnUpdatePoint(
                                                    hitPointIndex,
                                                    initialP.x + (totalRotDx / scale),
                                                    initialP.y + (totalRotDy / scale)
                                                )
                                            }
                                        } else if (hitLineIndex != -1) {
                                            val p1Initial = initialPositions[hitLineIndex]
                                            val p2Initial = initialPositions[hitLineIndex + 1]

                                            val isP1Locked = lockedP != null && abs(p1Initial.x - lockedP.x) < 0.000001 && abs(p1Initial.y - lockedP.y) < 0.000001
                                            val isP2Locked = lockedP != null && abs(p2Initial.x - lockedP.x) < 0.000001 && abs(p2Initial.y - lockedP.y) < 0.000001

                                            val dx = totalRotDx / scale
                                            val dy = totalRotDy / scale

                                            val finalX1 = if (isP1Locked) p1Initial.x else p1Initial.x + dx
                                            val finalY1 = if (isP1Locked) p1Initial.y else p1Initial.y + dy

                                            val finalX2 = if (isP2Locked) p2Initial.x else p2Initial.x + dx
                                            val finalY2 = if (isP2Locked) p2Initial.y else p2Initial.y + dy

                                            viewModel.updateLine(
                                                hitLineIndex, finalX1, finalY1,
                                                hitLineIndex + 1, finalX2, finalY2,
                                                false
                                            )
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

                            // EXCLUSIVE LINE DRAWING LOGIC
                            // Only show distance if the line is selected AND no point is actively selected.
                            if (isLineSelected && selectedPointIndex == -1) {
                                val length = if (isGPS) earthDistanceMeters(p1, p2) else CsvDxfUtils.distance(p1, p2)
                                val unit = if (isGPS) "m" else "units"

                                val midX = (screenP1.x + screenP2.x) / 2f
                                val midY = (screenP1.y + screenP2.y) / 2f

                                // Formatted distance to 3 decimal places
                                val text = "<-- ${String.format(Locale.US, "%.3f", length)} $unit -->"

                                val textLayoutResult = textMeasurer.measure(
                                    text = text,
                                    style = TextStyle(
                                        color = selectedColor,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        background = Color.Black.copy(alpha = 0.6f)
                                    )
                                )

                                // Calculate angle of the line in degrees for rotation
                                val dx = screenP2.x - screenP1.x
                                val dy = screenP2.y - screenP1.y
                                var visualAngleDegrees = atan2(dy, dx) * (180f / PI.toFloat())

                                // Keep text readable left-to-right
                                if (visualAngleDegrees > 90f || visualAngleDegrees < -90f) {
                                    visualAngleDegrees += 180f
                                }

                                // Use pivot = Offset.Zero so we rotate perfectly around the line's center point
                                translate(left = midX, top = midY) {
                                    rotate(degrees = visualAngleDegrees, pivot = Offset.Zero) {
                                        // tx centers the text horizontally on the pivot
                                        val tx = -textLayoutResult.size.width / 2f

                                        // ty pushes the text vertically "up" relative to the rotated line.
                                        // The 15f offset is absolute screen pixels, unaffected by map zoom.
                                        val ty = -textLayoutResult.size.height.toFloat() - 15f

                                        drawText(
                                            textLayoutResult = textLayoutResult,
                                            topLeft = Offset(tx, ty)
                                        )
                                    }
                                }
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

            val activeSp = points.getOrNull(selectedPointIndex)
            val activeLp = points.getOrNull(lockedPointIndex)

            points.forEachIndexed { index, point ->
                if (point.x.isFinite() && point.y.isFinite()) {
                    // Coordinates matched selection mapping handles exact overlap grouping natively
                    val isSelected = activeSp != null && abs(point.x - activeSp.x) < 0.000001 && abs(point.y - activeSp.y) < 0.000001
                    val isLocked = activeLp != null && abs(point.x - activeLp.x) < 0.000001 && abs(point.y - activeLp.y) < 0.000001
                    val isAreaSelected = isAreaMode && selectedAreaIndices.any { areaIdx ->
                        val ap = points.getOrNull(areaIdx)
                        ap != null && abs(point.x - ap.x) < 0.000001 && abs(point.y - ap.y) < 0.000001
                    }
                    val isConnectTarget = isConnectModePending && isSelected

                    val radius = if (isSelected || isAreaSelected || isLocked) 15f else 8f

                    val color = if (isConnectTarget) Color.Red
                    else if (isAreaSelected) areaSelectedDotColor
                    else if (isLocked) Color.Magenta
                    else if (isSelected) selectedColor
                    else dotColor

                    val screenPos = worldToScreen(point)
                    drawCircle(color = color, radius = radius, center = screenPos)

                    if (isLocked) {
                        drawCircle(color = Color.White, radius = radius * 0.4f, center = screenPos)
                    }

                    // EXCLUSIVE POINT LOGIC
                    // Formatting coordinates to exactly 7 decimal places
                    if (isSelected) {
                        labelsToDraw.add(screenPos to "(${String.format(Locale.US, "%.7f", point.x)}, ${String.format(Locale.US, "%.7f", point.y)})")
                    }
                }
            }

            if (displayAreaText != null && selectedAreaPos != null) {
                val pos = selectedAreaPos!!
                if (pos.x.isFinite() && pos.y.isFinite()) {
                    labelsToDraw.add(worldToScreen(pos) to displayAreaText)
                }
            }

            // Distinct deduplicates any overlapping label texts seamlessly
            labelsToDraw.distinct().forEach { (screenPos, text) ->
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

        // Overlay Parameter Box handling the Unit conversions
        if (calculatedBaseArea != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Intercepts clicks so canvas does NOT hide it
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Area Parameters", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (isGpsArea) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("m²", "Hectares", "ft²", "Acres").forEachIndexed { index, label ->
                                Surface(
                                    modifier = Modifier.clickable { selectedAreaUnit = index },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selectedAreaUnit == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selectedAreaUnit == index) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Standard Coordinate Area (No Unit Conversion)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                    viewModel.deletePointWithGap(selectedPointIndex)
                    selectedPointIndex = -1
                    calculatedBaseArea = null
                    lockedPointIndex = -1
                }
                else if (selectedLineIndex != -1) {
                    viewModel.breakLine(selectedLineIndex)
                    selectedLineIndex = -1
                    calculatedBaseArea = null
                }
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

                        calculatedBaseArea = area
                        isGpsArea = isGPS
                        selectedAreaUnit = 0

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
                    calculatedBaseArea = null
                    selectedAreaPos = null
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