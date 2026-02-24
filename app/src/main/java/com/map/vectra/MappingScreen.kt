package com.map.vectra

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
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

    // Limit preview to avoid overwhelming the UI
    val previewRows = rawData.take(15)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Import Configuration",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 16.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel", fontSize = 16.sp)
                    }
                    Button(
                        onClick = { onConfirm(selectedX, selectedY) },
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import & Map", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // --- STEP 1: AXIS CONFIGURATION ---
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("1", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Assign Coordinates",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    // FIXED: Replaced weight(1f) with identical explicit widths to make them perfectly even
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.Center, // Center everything
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.width(130.dp)) {
                            Text("X - Axis (Longitude)", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            ColumnSelector(headers, columnsCount, selectedX) { selectedX = it }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Swap",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    // Quick swap action
                                    val temp = selectedX
                                    selectedX = selectedY
                                    selectedY = temp
                                }
                                .padding(4.dp)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.width(130.dp)) {
                            Text("Y - Axis (Latitude)", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            ColumnSelector(headers, columnsCount, selectedY) { selectedY = it }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- STEP 2: DATA PREVIEW ---
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("2", color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Verify Data Mapping",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Showing first ${previewRows.size} rows. Highlighted columns will be imported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 44.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // The Table Container
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                LazyRow(modifier = Modifier.fillMaxSize()) {
                    item {
                        LazyColumn(modifier = Modifier.fillMaxHeight()) {
                            itemsIndexed(previewRows) { rowIndex, row ->

                                val isHeader = rowIndex == 0
                                val rowBackgroundColor by animateColorAsState(
                                    targetValue = when {
                                        isHeader -> MaterialTheme.colorScheme.surfaceVariant
                                        rowIndex % 2 == 0 -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.background.copy(alpha = 0.3f)
                                    }
                                )

                                Row(
                                    modifier = Modifier
                                        .background(rowBackgroundColor)
                                        .padding(horizontal = 12.dp, vertical = if (isHeader) 16.dp else 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Row Index Counter
                                    Text(
                                        text = if (isHeader) "#" else rowIndex.toString(),
                                        modifier = Modifier.width(36.dp),
                                        fontWeight = if (isHeader) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = if (isHeader) 14.sp else 13.sp
                                    )

                                    row.forEachIndexed { colIndex, cell ->
                                        val isX = colIndex == selectedX
                                        val isY = colIndex == selectedY
                                        val isSelectedCol = isX || isY

                                        // Dynamic styling based on selection mapping
                                        val cellColor = when {
                                            isHeader && isX -> MaterialTheme.colorScheme.primary
                                            isHeader && isY -> MaterialTheme.colorScheme.secondary
                                            isHeader -> MaterialTheme.colorScheme.onSurfaceVariant
                                            isSelectedCol -> MaterialTheme.colorScheme.onSurface
                                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        }

                                        val cellWeight = when {
                                            isHeader -> FontWeight.ExtraBold
                                            isSelectedCol -> FontWeight.SemiBold
                                            else -> FontWeight.Normal
                                        }

                                        // Cell Box with optional selection highlight background
                                        Box(
                                            modifier = Modifier
                                                .width(130.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (!isHeader && isX) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                                    else if (!isHeader && isY) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                                                    else Color.Transparent
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = cell,
                                                color = cellColor,
                                                fontWeight = cellWeight,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = if (isHeader) 14.sp else 13.sp
                                            )
                                        }
                                    }
                                }

                                if (isHeader) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 2.dp)
                                } else {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ColumnSelector(headers: List<String>, count: Int, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = if (selected < headers.size && headers[selected].isNotBlank()) headers[selected] else "Column $selected"

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .clip(RoundedCornerShape(12.dp))
        ) {
            for (i in 0 until count) {
                val label = if (i < headers.size && headers[i].isNotBlank()) headers[i] else "Column $i"
                val isSelected = i == selected

                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = { onSelect(i); expanded = false },
                    modifier = if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) else Modifier
                )
            }
        }
    }
}