package com.map.vectra

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewScreen(
    points: List<PointData>,
    customLines: List<Pair<Int, Int>>,
    onBack: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT

    var pitch by rememberSaveable { mutableFloatStateOf(45f) }
    var yaw by rememberSaveable { mutableFloatStateOf(45f) }
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var panX by rememberSaveable { mutableFloatStateOf(0f) }
    var panY by rememberSaveable { mutableFloatStateOf(0f) }
    var isPanMode by rememberSaveable { mutableStateOf(false) }

    var isDockExpanded by rememberSaveable { mutableStateOf(true) }
    var dockOffset by remember { mutableStateOf(Offset.Zero) }
    var showColorDialog by rememberSaveable { mutableStateOf(false) }

    var extrusionHeightText by rememberSaveable { mutableStateOf("0.0") }
    var actualExtrusion by rememberSaveable { mutableFloatStateOf(0f) }

    val defaultDotColor = MaterialTheme.colorScheme.primary
    val defaultLineColor = MaterialTheme.colorScheme.secondary
    val defaultCustomLineColor = MaterialTheme.colorScheme.tertiary

    var dotColor by remember { mutableStateOf(defaultDotColor) }
    var lineColor by remember { mutableStateOf(defaultLineColor) }
    var customLineColor by remember { mutableStateOf(defaultCustomLineColor) }

    val backgroundColor = MaterialTheme.colorScheme.surface

    val validPoints = remember(points) {
        points.filter { it.x.isFinite() && it.y.isFinite() }
    }

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isPanMode) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (zoom.isFinite()) {
                            scale *= zoom
                            scale = scale.coerceIn(0.1f, 1000f)
                        }

                        if (isPanMode) {
                            panX += pan.x
                            panY += pan.y
                        } else {
                            yaw -= pan.x * 0.5f
                            pitch += pan.y * 0.5f
                            pitch = pitch.coerceIn(0f, 90f) // Keep pitch between top-down and flat
                        }
                    }
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)

            // FIX: Ensure min/max uses Double correctly
            val minX = validPoints.minOfOrNull { it.x } ?: 0.0
            val maxX = validPoints.maxOfOrNull { it.x } ?: 0.0
            val minY = validPoints.minOfOrNull { it.y } ?: 0.0
            val maxY = validPoints.maxOfOrNull { it.y } ?: 0.0

            val cx = if (validPoints.isNotEmpty()) (minX + maxX) / 2.0 else 0.0
            val cy = if (validPoints.isNotEmpty()) (minY + maxY) / 2.0 else 0.0

            // NEW: GPS Aspect Ratio sync - Double precision
            val isGPS = validPoints.isNotEmpty() && validPoints.all { abs(it.x) <= 180.0 && abs(it.y) <= 90.0 }
            val latScaleFactor = if (isGPS) cos(cy * (PI / 180.0)) else 1.0

            val diffX = ((maxX - minX) * latScaleFactor).coerceAtLeast(0.000001)
            val diffY = (maxY - minY).coerceAtLeast(0.000001)
            val maxDim = max(diffX, diffY)

            val baseScale = (min(size.width, size.height) * 0.4) / maxDim
            val finalScale = baseScale * scale

            // NEW: True Map-based 3D Projection (Z is Up, XY is Ground) using Double inputs
            fun project(xRaw: Double, yRaw: Double, z: Float): Offset {
                // Apply GPS aspect ratio
                val dx = (xRaw - cx) * latScaleFactor
                val dy = (yRaw - cy)
                val dz = z.toDouble()

                val pR = pitch * (PI / 180.0) // Tilt camera up/down
                val yR = yaw * (PI / 180.0)   // Spin map like a turntable

                // 1. Yaw (Rotate around Z axis)
                val x1 = dx * cos(yR) - dy * sin(yR)
                val y1 = dx * sin(yR) + dy * cos(yR)

                // 2. Pitch (Rotate around X axis)
                val x2 = x1
                val y2 = y1 * cos(pR) - dz * sin(pR)

                // 3. Map to Screen (Invert Y so North is UP on the screen)
                return Offset(
                    x = (x2 * finalScale + center.x + panX).toFloat(),
                    y = (-y2 * finalScale + center.y + panY).toFloat()
                )
            }

            // Normalizing extrusion so input of "1.0" looks proportional relative to map size
            val scaledExtrusion = (actualExtrusion * (maxDim / 10.0)).toFloat()

            if (validPoints.size > 1) {
                for (i in 0 until validPoints.size - 1) {
                    val pt1 = validPoints[i]
                    val pt2 = validPoints[i + 1]

                    val proj1_floor = project(pt1.x, pt1.y, 0f)
                    val proj2_floor = project(pt2.x, pt2.y, 0f)

                    val proj1_ceil = project(pt1.x, pt1.y, scaledExtrusion)
                    val proj2_ceil = project(pt2.x, pt2.y, scaledExtrusion)

                    drawLine(lineColor, proj1_floor, proj2_floor, strokeWidth = 4f)

                    if (scaledExtrusion > 0) {
                        drawLine(lineColor.copy(alpha = 0.5f), proj1_ceil, proj2_ceil, strokeWidth = 4f)
                        drawLine(lineColor.copy(alpha = 0.3f), proj1_floor, proj1_ceil, strokeWidth = 2f)

                        // Close the walls for the last segment
                        if (i == validPoints.size - 2) {
                            drawLine(lineColor.copy(alpha = 0.3f), proj2_floor, proj2_ceil, strokeWidth = 2f)
                        }
                    }
                }
            }

            val pointMap = validPoints.associateBy { it.id }
            customLines.forEach { (id1, id2) ->
                val pt1 = pointMap[id1]
                val pt2 = pointMap[id2]
                if (pt1 != null && pt2 != null) {
                    val proj1_floor = project(pt1.x, pt1.y, 0f)
                    val proj2_floor = project(pt2.x, pt2.y, 0f)

                    val proj1_ceil = project(pt1.x, pt1.y, scaledExtrusion)
                    val proj2_ceil = project(pt2.x, pt2.y, scaledExtrusion)

                    drawLine(customLineColor, proj1_floor, proj2_floor, strokeWidth = 4f)

                    if (scaledExtrusion > 0) {
                        drawLine(customLineColor.copy(alpha = 0.5f), proj1_ceil, proj2_ceil, strokeWidth = 4f)
                    }
                }
            }

            validPoints.forEach { pt ->
                val proj_floor = project(pt.x, pt.y, 0f)
                drawCircle(dotColor, radius = 8f, center = proj_floor)

                if (scaledExtrusion > 0) {
                    val proj_ceil = project(pt.x, pt.y, scaledExtrusion)
                    drawCircle(dotColor.copy(alpha = 0.5f), radius = 6f, center = proj_ceil)
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

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)),
            elevation = CardDefaults.cardElevation(4.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = extrusionHeightText,
                    onValueChange = {
                        extrusionHeightText = it
                        val parsed = it.toFloatOrNull()
                        if (parsed != null) {
                            actualExtrusion = parsed
                        }
                    },
                    label = { Text("Z-Height") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.width(120.dp),
                    textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
                )
            }
        }

        val dockItems = mutableListOf<@Composable () -> Unit>()

        // Updated button shortcuts for the new Map-based angles
        dockItems.add { TextButton(onClick = { pitch = 0f; yaw = 0f; panX = 0f; panY = 0f }) { Text("Top") } }
        dockItems.add { Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = if (isPortrait) Modifier.height(24.dp).width(1.dp) else Modifier.width(24.dp).height(1.dp)) }
        dockItems.add { TextButton(onClick = { pitch = 90f; yaw = 90f; panX = 0f; panY = 0f }) { Text("Right") } }
        dockItems.add { TextButton(onClick = { pitch = 90f; yaw = -90f; panX = 0f; panY = 0f }) { Text("Left") } }
        dockItems.add { Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = if (isPortrait) Modifier.height(24.dp).width(1.dp) else Modifier.width(24.dp).height(1.dp)) }
        dockItems.add { TextButton(onClick = { pitch = 90f; yaw = 0f; panX = 0f; panY = 0f }) { Text("Front") } }
        dockItems.add { TextButton(onClick = { pitch = 90f; yaw = 180f; panX = 0f; panY = 0f }) { Text("Back") } }
        dockItems.add { Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = if (isPortrait) Modifier.height(24.dp).width(1.dp) else Modifier.width(24.dp).height(1.dp)) }
        dockItems.add { Button(onClick = { pitch = 60f; yaw = 45f; panX = 0f; panY = 0f }) { Text("ISO") } }

        dockItems.add { Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = if (isPortrait) Modifier.height(24.dp).width(1.dp) else Modifier.width(24.dp).height(1.dp)) }
        dockItems.add {
            IconButton(onClick = { showColorDialog = true }) {
                Icon(Icons.Default.Palette, "Color Layers", tint = MaterialTheme.colorScheme.primary)
            }
        }

        dockItems.add { Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = if (isPortrait) Modifier.height(24.dp).width(1.dp) else Modifier.width(24.dp).height(1.dp)) }
        dockItems.add {
            IconToggleButton(checked = isPanMode, onCheckedChange = { isPanMode = it }) {
                Icon(if (isPanMode) Icons.Default.PanTool else Icons.Default.Refresh, "Toggle Pan/Rotate", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset { IntOffset(dockOffset.x.roundToInt(), dockOffset.y.roundToInt()) }
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress { change, dragAmount ->
                        change.consume()
                        dockOffset += dragAmount
                    }
                }
        ) {
            Card(
                shape = RoundedCornerShape(50),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                if (isPortrait) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                        AnimatedVisibility(
                            visible = isDockExpanded,
                            enter = expandHorizontally(expandFrom = Alignment.End),
                            exit = shrinkHorizontally(shrinkTowards = Alignment.End)
                        ) {
                            LazyRow(
                                modifier = Modifier.widthIn(max = 280.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(dockItems.size) { index ->
                                    dockItems[index]()
                                }
                            }
                        }
                        IconButton(onClick = {
                            isDockExpanded = !isDockExpanded
                            if (!isDockExpanded) dockOffset = Offset.Zero
                        }) {
                            Icon(if (isDockExpanded) Icons.Default.Close else Icons.Default.Menu, "Toggle Dock")
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                        AnimatedVisibility(
                            visible = isDockExpanded,
                            enter = expandVertically(expandFrom = Alignment.Bottom),
                            exit = shrinkVertically(shrinkTowards = Alignment.Bottom)
                        ) {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 280.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                items(dockItems.size) { index ->
                                    dockItems[index]()
                                }
                            }
                        }
                        IconButton(onClick = {
                            isDockExpanded = !isDockExpanded
                            if (!isDockExpanded) dockOffset = Offset.Zero
                        }) {
                            Icon(if (isDockExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, "Toggle Dock")
                        }
                    }
                }
            }
        }

        if (showColorDialog) {
            AlertDialog(
                onDismissRequest = { showColorDialog = false },
                title = { Text("Layer Color Coding") },
                text = {
                    Column {
                        LayerColorRow("Points", dotColor) { dotColor = it }
                        Spacer(modifier = Modifier.height(12.dp))
                        LayerColorRow("Sequential Lines", lineColor) { lineColor = it }
                        Spacer(modifier = Modifier.height(12.dp))
                        LayerColorRow("Custom Interlinks", customLineColor) { customLineColor = it }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showColorDialog = false }) {
                        Text("Done")
                    }
                }
            )
        }
    }
}

@Composable
fun LayerColorRow(label: String, currentColor: Color, onColorSelect: (Color) -> Unit) {
    val colors = listOf(
        Color.Red, Color.Green, Color.Blue, Color.Yellow,
        Color.Cyan, Color.Magenta, Color.White, Color.Black,
        Color.Gray, Color(0xFFFFA500), Color(0xFF800080)
    )
    Column {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(colors) { color ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(color, RoundedCornerShape(50))
                        .border(
                            width = if (currentColor == color) 3.dp else 1.dp,
                            color = if (currentColor == color) MaterialTheme.colorScheme.onSurface else Color.LightGray,
                            shape = RoundedCornerShape(50)
                        )
                        .clickable { onColorSelect(color) }
                )
            }
        }
    }
}