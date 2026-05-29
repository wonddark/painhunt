package com.painhunt.ui.sources

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.painhunt.domain.Subreddit
import com.painhunt.presentation.SourcesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(viewModel: SourcesViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var newSourceName by remember { mutableStateOf("") }
    var editingSource by remember { mutableStateOf<Subreddit?>(null) }
    var editName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sources") },
                windowInsets = WindowInsets(0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add source")
            }
        }
    ) { padding ->
        if (state.isLoading && state.sources.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.error != null) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::load) { Text("Retry") }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(state.sources, key = { it.id }) { source ->
                    ListItem(
                        headlineContent = { Text("r/${source.name}") },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = source.active,
                                    onCheckedChange = { viewModel.setActive(source.id, it) },
                                )
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = {
                                    editingSource = source
                                    editName = source.name
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                                }
                                IconButton(onClick = { viewModel.remove(source.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; newSourceName = "" },
            title = { Text("Add Source") },
            text = {
                OutlinedTextField(
                    value = newSourceName,
                    onValueChange = { newSourceName = it },
                    label = { Text("Source name (e.g. SomebodyMakeThis)") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newSourceName.isNotBlank()) {
                        viewModel.add(newSourceName)
                        showAddDialog = false
                        newSourceName = ""
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; newSourceName = "" }) { Text("Cancel") }
            }
        )
    }

    editingSource?.let { source ->
        AlertDialog(
            onDismissRequest = { editingSource = null; editName = "" },
            title = { Text("Edit Source") },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Source name (without r/ prefix)") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editName.isNotBlank()) {
                        viewModel.rename(source.id, editName)
                        editingSource = null
                        editName = ""
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingSource = null; editName = "" }) { Text("Cancel") }
            }
        )
    }
}
