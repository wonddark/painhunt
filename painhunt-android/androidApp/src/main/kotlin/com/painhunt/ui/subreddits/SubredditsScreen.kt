package com.painhunt.ui.subreddits

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.painhunt.domain.Subreddit
import com.painhunt.presentation.SubredditsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubredditsScreen(viewModel: SubredditsViewModel) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var newSubredditName by remember { mutableStateOf("") }
    var editingSubreddit by remember { mutableStateOf<Subreddit?>(null) }
    var editName by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Subreddits") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add subreddit")
            }
        }
    ) { padding ->
        if (state.isLoading && state.subreddits.isEmpty()) {
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
                items(state.subreddits, key = { it.id }) { subreddit ->
                    ListItem(
                        headlineContent = { Text("r/${subreddit.name}") },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = subreddit.active,
                                    onCheckedChange = { viewModel.setActive(subreddit.id, it) },
                                )
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = {
                                    editingSubreddit = subreddit
                                    editName = subreddit.name
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                                }
                                IconButton(onClick = { viewModel.remove(subreddit.id) }) {
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
            onDismissRequest = { showAddDialog = false; newSubredditName = "" },
            title = { Text("Add Subreddit") },
            text = {
                OutlinedTextField(
                    value = newSubredditName,
                    onValueChange = { newSubredditName = it },
                    label = { Text("Subreddit name (e.g. SomebodyMakeThis)") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newSubredditName.isNotBlank()) {
                        viewModel.add(newSubredditName)
                        showAddDialog = false
                        newSubredditName = ""
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; newSubredditName = "" }) { Text("Cancel") }
            }
        )
    }

    editingSubreddit?.let { subreddit ->
        AlertDialog(
            onDismissRequest = { editingSubreddit = null; editName = "" },
            title = { Text("Edit Subreddit") },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Subreddit name (without r/ prefix)") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editName.isNotBlank()) {
                        viewModel.rename(subreddit.id, editName)
                        editingSubreddit = null
                        editName = ""
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingSubreddit = null; editName = "" }) { Text("Cancel") }
            }
        )
    }
}
