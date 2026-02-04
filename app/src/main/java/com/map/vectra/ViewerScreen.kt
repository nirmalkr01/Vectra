package com.map.vectra

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

@Composable
fun ViewerScreen(
    points: List<PointData>,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
    onExport: () -> Unit,
    onUpdatePoint: (Int, Float, Float) -> Unit,
    onSaveState: () -> Unit,
    onUndo: () -> Unit,
    canUndo: Boolean
) {
    // --- State Holders ---
    // Critical: Update state refs so gestures always use latest data
    val currentPoints by rememberUpdatedState(points)
    val currentOnUpdatePoint by rememberUpdatedState(onUpdatePoint)

    // View Transform
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotation by remember { mutableFloatStateOf(0f) }

    // Selection
    var selectedPointIndex by remember { mutableIntStateOf(-1) }
    var selectedLineIndex by remember { mutableIntStateOf(-1) }
    var selectedAreaInfo by remember { mutableStateOf<String?>(null) }
    var selectedAreaPos by remember { mutableStateOf<PointData?>(null) }
    var showLines by remember { mutableStateOf(true) }

    // UI State - Default OPEN
    var isMenuExpanded by remember { mutableStateOf(true) }
    val textMeasurer = rememberTextMeasurer()

    // Colors
    val backgroundColor = MaterialTheme.colorScheme.surface
    val gridLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    val dotColor = MaterialTheme.colorScheme.primary
    val selectedColor = Color.Yellow
    val lineColor = MaterialTheme.colorScheme.secondary
    val textColor = MaterialTheme.colorScheme.onSurface

    // --- Helpers ---
    fun screenToWorld(touch: Offset): PointData {
        val dx = touch.x - offset.x
        val dy = touch.y - offset.y
        val rad = -rotation * (PI / 180f)
        val cos = cos(rad)
        val sin = sin(rad)
        val rotatedX = (dx * cos - dy * sin)
        val rotatedY = (dx * sin + dy * cos)
        val worldX = rotatedX / scale
        val worldY = rotatedY / scale
        return PointData(0, worldX.toFloat(), worldY.toFloat())
    }

    fun worldToScreen(p: PointData): Offset {
        val rad = rotation * (PI / 180f)
        val cos = cos(rad)
        val sin = sin(rad)
        val wx = p.x * scale
        val wy = p.y * scale
        val rx = (wx * cos - wy * sin)
        val ry = (wx * sin + wy * cos)
        return Offset((rx + offset.x).toFloat(), (ry + offset.y).toFloat())
    }

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // --- LAYER 1: SCREEN TRANSFORM (Background) ---
                // This handles panning/zooming ONLY if the event wasn't consumed by Layer 2
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, rotate ->
                        val oldScale = scale
                        scale *= zoom
                        scale = scale.coerceIn(0.1f, 1000f)

                        val rad = rotate * (PI / 180f)
                        val cos = cos(rad)
                        val sin = sin(rad)

                        val cx = centroid.x - offset.x
                        val cy = centroid.y - offset.y
                        val newCx = cx * cos - cy * sin
                        val newCy = cx * sin + cy * cos
                        val rotOffsetX = cx - newCx
                        val rotOffsetY = cy - newCy

                        rotation += rotate

                        val totalPanX = pan.x + rotOffsetX.toFloat()
                        val totalPanY = pan.y + rotOffsetY.toFloat()

                        offset += Offset(totalPanX, totalPanY)
                    }
                }
                // --- LAYER 2: POINT INTERACTION (Foreground) ---
                // This logic runs "on top". If we hit a point, we consume events so Layer 1 doesn't see them.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val worldPos = screenToWorld(down.position)
                        val hitRadius = (50f / scale).coerceAtLeast(20f) // Generous hit area

                        // 1. Check Point Hit
                        val hitIndex = currentPoints.indexOfFirst {
                            CsvDxfUtils.distance(it, worldPos) < hitRadius
                        }

                        if (hitIndex != -1) {
                            // --- HIT: POINT DETECTED ---
                            // 1. Consume the DOWN event immediately.
                            // This stops Layer 1 (Transform) from starting a pan/zoom.
                            down.consume()

                            // 2. Select Point (Yellow)
                            selectedPointIndex = hitIndex
                            selectedLineIndex = -1
                            selectedAreaInfo = null
                            onSaveState() // Save history for Undo

                            // 3. Enter Drag Loop
                            // We manually track the pointer to ensure unrestricted movement
                            var dragPointerId = down.id

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.find { it.id == dragPointerId }

                                if (change == null || !change.pressed) {
                                    break // Finger lifted
                                }

                                val dragAmount = change.position - change.previousPosition
                                if (dragAmount.getDistance() > 0f) {
                                    // Calculate movement in World Space
                                    val rad = -rotation * (PI / 180f)
                                    val rotDx = (dragAmount.x * cos(rad) - dragAmount.y * sin(rad)).toFloat()
                                    val rotDy = (dragAmount.x * sin(rad) + dragAmount.y * cos(rad)).toFloat()

                                    // Update Point directly
                                    if (hitIndex < currentPoints.size) {
                                        val curr = currentPoints[hitIndex]
                                        val newX = curr.x + (rotDx / scale)
                                        val newY = curr.y + (rotDy / scale)

                                        // CRASH FIX: Ensure coordinates are finite (not NaN or Infinite)
                                        // This prevents crashes when points overlap or math breaks in negative coordinates
                                        if (newX.isFinite() && newY.isFinite()) {
                                            currentOnUpdatePoint(hitIndex, newX, newY)
                                        }
                                    }
                                }
                                // Consume EVERY move event so the screen never scrolls
                                change.consume()
                            }
                        } else {
                            // --- MISS: CHECK TAP LOGIC ---
                            // We didn't hit a point. We DO NOT consume the down event.
                            // We let Layer 1 handle the Pan/Zoom.
                            // BUT, we need to handle "Single Tap on Empty Space" to deselect.
                            // We can wait to see if it was just a tap (no drag).

                            val up = waitForUpOrCancellation()
                            if (up != null) {
                                // It was a tap (finger went down and up without significant move consumed elsewhere)
                                // Handle Deselection / Line Selection / Area
                                val tapPoint = screenToWorld(up.position)

                                // Check Lines
                                var lineFound = false
                                if (showLines && currentPoints.size > 1) {
                                    for (i in 0 until currentPoints.size - 1) {
                                        if (CsvDxfUtils.distanceToSegment(tapPoint, currentPoints[i], currentPoints[i + 1]) < hitRadius) {
                                            selectedLineIndex = if (selectedLineIndex == i) -1 else i
                                            selectedPointIndex = -1
                                            selectedAreaInfo = null
                                            lineFound = true
                                            break
                                        }
                                    }
                                }

                                // Check Area
                                if (!lineFound && currentPoints.size >= 4) {
                                    if (CsvDxfUtils.isInsidePolygon(tapPoint, currentPoints)) {
                                        if (selectedAreaInfo == null) {
                                            val area = CsvDxfUtils.calculatePolygonArea(currentPoints)
                                            selectedAreaInfo = "Area: ${"%.2f".format(area)}"
                                            selectedAreaPos = tapPoint
                                            selectedPointIndex = -1
                                            selectedLineIndex = -1
                                        } else {
                                            selectedAreaInfo = null
                                        }
                                        lineFound = true
                                    }
                                }

                                // Deselect All
                                if (!lineFound) {
                                    selectedPointIndex = -1
                                    selectedLineIndex = -1
                                    selectedAreaInfo = null
                                }
                            }
                        }
                    }
                }
        ) {
            val canvasSize = size
            // --- DRAWING ---
            translate(left = offset.x, top = offset.y) {
                rotate(degrees = rotation, pivot = Offset.Zero) {
                    scale(scale = scale, pivot = Offset.Zero) {

                        // 1. Grid (Capped loop to prevent memory issues)
                        val gridSize = 100f
                        for (i in -200..200) {
                            val pos = i * gridSize
                            drawLine(gridLineColor, Offset(pos, -20000f), Offset(pos, 20000f))
                            drawLine(gridLineColor, Offset(-20000f, pos), Offset(20000f, pos))
                        }

                        val textSize = (14 / scale).coerceIn(4f, 40f).sp

                        // 2. Lines
                        if (showLines && points.size > 1) {
                            for (i in 0 until points.size - 1) {
                                val p1 = points[i]
                                val p2 = points[i + 1]
                                drawLine(color = lineColor, start = Offset(p1.x, p1.y), end = Offset(p2.x, p2.y), strokeWidth = 3f / scale)

                                if (i == selectedLineIndex) {
                                    val midX = (p1.x + p2.x) / 2
                                    val midY = (p1.y + p2.y) / 2
                                    val length = CsvDxfUtils.distance(p1, p2)
                                    val text = "<-- ${"%.1f".format(length)} -->"

                                    val angleRad = atan2(p2.y - p1.y, p2.x - p1.x)
                                    var angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()
                                    if (angleDeg > 90 || angleDeg < -90) angleDeg += 180

                                    // CRASH FIX: Bounds Check
                                    val screenPos = worldToScreen(PointData(0, midX, midY))
                                    if (screenPos.x > -200 && screenPos.x < canvasSize.width + 200 &&
                                        screenPos.y > -200 && screenPos.y < canvasSize.height + 200) {
                                        rotate(degrees = angleDeg, pivot = Offset(midX, midY)) {
                                            drawText(textMeasurer = textMeasurer, text = text, topLeft = Offset(midX - (30 / scale), midY - (10 / scale)), style = TextStyle(color = textColor, fontSize = textSize, background = backgroundColor.copy(alpha = 0.8f)))
                                        }
                                    }
                                }
                            }
                            if (points.size > 2) {
                                val pLast = points.last()
                                val pFirst = points.first()
                                drawLine(lineColor.copy(alpha = 0.5f), Offset(pLast.x, pLast.y), Offset(pFirst.x, pFirst.y), strokeWidth = 2f / scale)
                            }
                        }

                        // 3. Points
                        points.forEachIndexed { index, point ->
                            val isSelected = index == selectedPointIndex
                            val radius = if (isSelected) 15f else 8f
                            val color = if (isSelected) selectedColor else dotColor

                            drawCircle(color = color, radius = radius / scale, center = Offset(point.x, point.y))

                            if (isSelected) {
                                // CRASH FIX: Bounds Check
                                val screenPos = worldToScreen(point)
                                if (screenPos.x > -200 && screenPos.x < canvasSize.width + 200 &&
                                    screenPos.y > -200 && screenPos.y < canvasSize.height + 200) {

                                    rotate(degrees = -rotation, pivot = Offset(point.x, point.y)) {
                                        drawText(
                                            textMeasurer = textMeasurer,
                                            text = "(${point.x.toInt()}, ${point.y.toInt()})",
                                            topLeft = Offset(point.x + (15 / scale), point.y - (25 / scale)),
                                            style = TextStyle(color = selectedColor, fontSize = textSize, fontWeight = FontWeight.Bold, background = Color.Black.copy(alpha = 0.6f))
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Area
                        if (selectedAreaInfo != null && selectedAreaPos != null) {
                            val screenPos = worldToScreen(selectedAreaPos!!)
                            if (screenPos.x > -200 && screenPos.x < canvasSize.width + 200 &&
                                screenPos.y > -200 && screenPos.y < canvasSize.height + 200) {
                                rotate(degrees = -rotation, pivot = Offset(selectedAreaPos!!.x, selectedAreaPos!!.y)) {
                                    drawText(textMeasurer = textMeasurer, text = selectedAreaInfo!!, topLeft = Offset(selectedAreaPos!!.x, selectedAreaPos!!.y), style = TextStyle(color = textColor, fontSize = textSize * 1.5, fontWeight = FontWeight.Bold, background = backgroundColor.copy(alpha = 0.8f)))
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- CONTROLS ---
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            FilledTonalIconButton(onClick = { showLines = !showLines }) {
                Text(if (showLines) "📐" else "⚫")
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                .clickable { isMenuExpanded = !isMenuExpanded }
                .padding(12.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isMenuExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = "Toggle",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AnimatedVisibility(visible = isMenuExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(onClick = onOpenEditor) { Text("Data Editor") }
                        Button(onClick = onExport) { Text("Export") }
                        Button(onClick = onUndo, enabled = canUndo) { Text("Undo") }
                    }
                }
            }
        }
    }
}