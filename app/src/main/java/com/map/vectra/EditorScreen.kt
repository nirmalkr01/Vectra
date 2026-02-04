package com.map.vectra

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    points: List<PointData>,
    onUpdatePoint: (Int, Float, Float) -> Unit,
    onDeletePoint: (Int) -> Unit,
    onNavigateToView: () -> Unit,
    onExport: () -> Unit
) {
    // Local state to hold changes before applying
    var localPoints by remember { mutableStateOf(points) }

    // Sync local state if the original points change (e.g., from Undo or Graph changes)
    LaunchedEffect(points) {
        localPoints = points
    }

    Column(
        // Dynamic Background (Dark or Light)
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        TopAppBar(
            title = { Text("Data Editor") },
            navigationIcon = {
                IconButton(onClick = onNavigateToView) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Graph"
                    )
                }
            },
            actions = {
                // Apply Button
                Button(
                    onClick = {
                        // Iterate and apply changes to the main ViewModel
                        localPoints.forEachIndexed { index, localPoint ->
                            val original = points.getOrNull(index)
                            // Only update if changed or if it's a new entry logic
                            if (original != localPoint) {
                                onUpdatePoint(index, localPoint.x, localPoint.y)
                            }
                        }
                    },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Apply")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface
            )
        )

        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant) // Slightly different shade
                .padding(8.dp)
        ) {
            Text("#", modifier = Modifier.weight(0.5f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("X-Coord", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Y-Coord", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(48.dp))
        }

        // List
        LazyColumn(
            modifier = Modifier.weight(1f).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(localPoints) { index, point ->
                PointRow(index, point,
                    onUpdate = { x, y ->
                        // Update local state only
                        val currentList = localPoints.toMutableList()
                        if (index in currentList.indices) {
                            currentList[index] = currentList[index].copy(x = x, y = y)
                            localPoints = currentList
                        }
                    },
                    onDelete = { onDeletePoint(index) }
                )
            }
        }
    }
}

@Composable
fun PointRow(
    index: Int,
    point: PointData,
    onUpdate: (Float, Float) -> Unit,
    onDelete: () -> Unit
) {
    // We use derived state for text fields to ensure they reflect the point data
    // but we don't update the external point immediately on every keystroke,
    // we just call onUpdate which now updates localPoints.

    Card(
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface // Card adapts to theme
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = (index + 1).toString(),
                modifier = Modifier.weight(0.5f),
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = point.x.toString(),
                onValueChange = { str ->
                    str.toFloatOrNull()?.let { num -> onUpdate(num, point.y) }
                },
                modifier = Modifier.weight(1f).padding(end = 4.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )

            OutlinedTextField(
                value = point.y.toString(),
                onValueChange = { str ->
                    str.toFloatOrNull()?.let { num -> onUpdate(point.x, num) }
                },
                modifier = Modifier.weight(1f).padding(end = 4.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}