package com.map.vectra

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun IconLayout(
    modifier: Modifier = Modifier,
    isPortrait: Boolean,
    isSelectionActive: Boolean,
    onDeleteClick: () -> Unit,
    isAddMode: Boolean,
    onAddModeChange: (Boolean) -> Unit,
    showLines: Boolean,
    onShowLinesChange: (Boolean) -> Unit,
    showConnectOption: Boolean,
    isConnectModePending: Boolean,
    onConnectModeChange: (Boolean) -> Unit,
    showLockPointOption: Boolean,                // NEW: Dictates if Point Lock toggle shows
    isPointLocked: Boolean,                      // NEW: Current status of the pinned lock
    onLockPointToggle: (Boolean) -> Unit,        // NEW: Action handler for lock
    isAreaMode: Boolean,
    onAreaModeClick: () -> Unit,
    lockScroll: Boolean,
    onLockScrollChange: (Boolean) -> Unit,
    lockRotation: Boolean,
    onLockRotationChange: (Boolean) -> Unit,
    lockZoom: Boolean,
    onLockZoomChange: (Boolean) -> Unit,
    onOpenEditor: () -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onExport: () -> Unit,
    onOpen3DView: () -> Unit
) {
    var isDockExpanded by remember { mutableStateOf(true) }
    var dockOffset by remember { mutableStateOf(Offset.Zero) }
    var showLockDialog by remember { mutableStateOf(false) }

    val dockItems = mutableListOf<@Composable () -> Unit>()

    // 1. Delete
    dockItems.add {
        IconButton(
            onClick = onDeleteClick,
            enabled = isSelectionActive
        ) {
            Icon(
                Icons.Default.Delete,
                "Delete Selected",
                tint = if (isSelectionActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }

    // 2. Add Point
    dockItems.add {
        IconToggleButton(checked = isAddMode, onCheckedChange = onAddModeChange) {
            Icon(Icons.Default.Add, "Add Point", tint = if (isAddMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
    }

    // 3. Toggle Lines
    dockItems.add {
        IconButton(onClick = { onShowLinesChange(!showLines) }) {
            Text(if (showLines) "📐" else "⚫", fontSize = 18.sp)
        }
    }

    // 4. Connect Point (Contextual)
    if (showConnectOption) {
        dockItems.add {
            IconToggleButton(checked = isConnectModePending, onCheckedChange = onConnectModeChange) {
                Icon(
                    if (isConnectModePending) Icons.Default.LinkOff else Icons.Default.Link,
                    "Link Point",
                    tint = if (isConnectModePending) Color.Green else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    // 5. Lock Point (Contextual to Point Selection)
    if (showLockPointOption) {
        dockItems.add {
            IconToggleButton(checked = isPointLocked, onCheckedChange = onLockPointToggle) {
                Icon(
                    if (isPointLocked) Icons.Default.PushPin else Icons.Default.Place,
                    contentDescription = "Lock Point",
                    tint = if (isPointLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    // 6. Area Calculation
    dockItems.add {
        IconButton(onClick = onAreaModeClick) {
            Icon(Icons.Default.Calculate, "Area", tint = if (isAreaMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }

    // 7. Lock Controls
    dockItems.add {
        IconButton(onClick = { showLockDialog = true }) {
            val isAnyLocked = lockScroll || lockRotation || lockZoom
            Icon(
                imageVector = if (isAnyLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = "Lock Canvas",
                tint = if (isAnyLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
    }

    // 8. Data Editor
    dockItems.add {
        IconButton(onClick = onOpenEditor) {
            Icon(Icons.Default.TableChart, "Data", tint = MaterialTheme.colorScheme.onSurface)
        }
    }

    // 9. Undo
    dockItems.add {
        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(Icons.Default.Undo, "Undo", tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }

    // 10. 3D View
    dockItems.add {
        IconButton(onClick = onOpen3DView) {
            Icon(Icons.Default.ViewInAr, "3D View", tint = MaterialTheme.colorScheme.primary)
        }
    }

    // 11. Export
    dockItems.add {
        IconButton(onClick = onExport) {
            Icon(Icons.Default.FileDownload, "Export", tint = MaterialTheme.colorScheme.primary)
        }
    }

    Box(
        modifier = modifier
            .offset { IntOffset(dockOffset.x.roundToInt(), dockOffset.y.roundToInt()) }
            .padding(16.dp)
            // Listen for a long-press to start dragging the layout
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress { change, dragAmount ->
                    change.consume()
                    dockOffset += dragAmount
                }
            }
    ) {
        Card(
            shape = RoundedCornerShape(50),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)),
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
                            modifier = Modifier.widthIn(max = 280.dp), // Limit width to allow scrolling if overflow
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
                        if (!isDockExpanded) dockOffset = Offset.Zero // Snap to bottom right corner
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
                            modifier = Modifier.heightIn(max = 280.dp), // Limit height to allow scrolling if overflow
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
                        if (!isDockExpanded) dockOffset = Offset.Zero // Snap to bottom right corner
                    }) {
                        Icon(if (isDockExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, "Toggle Dock")
                    }
                }
            }
        }
    }

    // Lock Canvas Dialog
    if (showLockDialog) {
        AlertDialog(
            onDismissRequest = { showLockDialog = false },
            title = { Text("Lock Canvas Controls") },
            text = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLockScrollChange(!lockScroll) }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(checked = lockScroll, onCheckedChange = onLockScrollChange)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Lock Drag (Pan)")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLockRotationChange(!lockRotation) }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(checked = lockRotation, onCheckedChange = onLockRotationChange)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Lock Rotation")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLockZoomChange(!lockZoom) }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(checked = lockZoom, onCheckedChange = onLockZoomChange)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Lock Zoom")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLockDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}