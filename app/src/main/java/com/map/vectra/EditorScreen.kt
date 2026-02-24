package com.map.vectra

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    points: List<PointData>,
    onUpdatePoint: (Int, Double, Double) -> Unit, // UPDATED TO DOUBLE
    onDeletePoint: (Int) -> Unit,
    onNavigateToView: () -> Unit
) {
    // Local state to hold changes before applying
    var localPoints by remember { mutableStateOf(points) }

    // Sync local state if the original points change (e.g., from Undo or Graph changes)
    LaunchedEffect(points) {
        localPoints = points
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Coordinate Editor", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateToView) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Graph"
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            // Iterate and apply changes to the main ViewModel
                            localPoints.forEachIndexed { index, localPoint ->
                                val original = points.getOrNull(index)
                                // Only update if changed
                                if (original != localPoint) {
                                    onUpdatePoint(index, localPoint.x, localPoint.y)
                                }
                            }
                            onNavigateToView()
                        },
                        modifier = Modifier.padding(end = 8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Table Container
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // --- TABLE HEADER ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#",
                            modifier = Modifier.weight(0.15f),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "X - Axis",
                            modifier = Modifier.weight(0.35f),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Y - Axis",
                            modifier = Modifier.weight(0.35f),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.weight(0.15f)) // Matches the delete button space
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                    // --- TABLE BODY ---
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(localPoints) { index, point ->
                            val rowColor = if (index % 2 == 0) {
                                Color.Transparent
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            }

                            PointRow(
                                index = index,
                                point = point,
                                backgroundColor = rowColor,
                                onUpdate = { x, y ->
                                    val currentList = localPoints.toMutableList()
                                    if (index in currentList.indices) {
                                        currentList[index] = currentList[index].copy(x = x, y = y)
                                        localPoints = currentList
                                    }
                                },
                                onDelete = { onDeletePoint(index) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PointRow(
    index: Int,
    point: PointData,
    backgroundColor: Color,
    onUpdate: (Double, Double) -> Unit, // UPDATED TO DOUBLE
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ID / Index
        Text(
            text = (index + 1).toString(),
            modifier = Modifier.weight(0.15f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )

        // X Input
        OutlinedTextField(
            value = if (point.x.isNaN()) "Gap" else point.x.toString(),
            onValueChange = { str ->
                str.toDoubleOrNull()?.let { num -> onUpdate(num, point.y) }
            },
            modifier = Modifier
                .weight(0.35f)
                .padding(horizontal = 4.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 14.sp),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Y Input
        OutlinedTextField(
            value = if (point.y.isNaN()) "Gap" else point.y.toString(),
            onValueChange = { str ->
                str.toDoubleOrNull()?.let { num -> onUpdate(point.x, num) }
            },
            modifier = Modifier
                .weight(0.35f)
                .padding(horizontal = 4.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 14.sp),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.secondary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Delete Button
        Box(
            modifier = Modifier.weight(0.15f),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}