package com.map.vectra

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MappingScreen(
    rawData: List<List<String>>,
    onConfirm: (Int, Int) -> Unit,
    onCancel: () -> Unit
) {
    var selectedX by remember { mutableStateOf(0) }
    var selectedY by remember { mutableStateOf(1) }
    val columnsCount = rawData.firstOrNull()?.size ?: 0

    // Headers detection: Attempt to get the first row as headers
    val headers = remember(rawData) {
        if (rawData.isNotEmpty()) rawData[0] else emptyList()
    }

    val previewRows = rawData.take(6)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Map Columns", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Select X Axis:", color = MaterialTheme.colorScheme.onBackground)
                ColumnSelector(headers, columnsCount, selectedX) { selectedX = it }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text("Select Y Axis:", color = MaterialTheme.colorScheme.onBackground)
                ColumnSelector(headers, columnsCount, selectedY) { selectedY = it }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Preview:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        LazyColumn(modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant)) {
            items(previewRows) { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                    row.forEach { cell ->
                        Text(
                            text = cell,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(80.dp).padding(end = 4.dp),
                            maxLines = 1
                        )
                    }
                }
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onConfirm(selectedX, selectedY) }) { Text("Confirm") }
        }
    }
}

@Composable
fun ColumnSelector(headers: List<String>, count: Int, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = if (selected < headers.size) headers[selected] else "Column $selected"

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selectedText, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (i in 0 until count) {
                // Determine label: Use header if available, else "Column i"
                val label = if (i < headers.size && headers[i].isNotBlank()) headers[i] else "Column $i"
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSelect(i); expanded = false }
                )
            }
        }
    }
}