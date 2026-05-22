package com.painhunt.ui.implementing

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.painhunt.presentation.ImplementingDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImplementingDetailScreen(
    implementationId: String,
    viewModel: ImplementingDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showAbandonDialog by remember { mutableStateOf(false) }

    LaunchedEffect(implementationId) { viewModel.load(implementationId) }
    LaunchedEffect(state.removed) { if (state.removed) onBack() }

    if (showAbandonDialog) {
        AlertDialog(
            onDismissRequest = { showAbandonDialog = false },
            title = { Text("Abandon implementation?") },
            text = { Text("This will permanently delete the implementation plan. All progress will be lost.") },
            confirmButton = {
                TextButton(onClick = { viewModel.remove(); showAbandonDialog = false }) {
                    Text("Abandon", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAbandonDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Implementation Plan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAbandonDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Abandon")
                    }
                },
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null && state.implementation == null -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                val impl = state.implementation ?: return@Scaffold
                val totalTasks = impl.goals.sumOf { it.tasks.size }
                val doneTasks = impl.goals.sumOf { g -> g.tasks.count { it.done } }
                val percent = if (totalTasks > 0) doneTasks * 100 / totalTasks else 0

                LazyColumn(
                    modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(impl.concept, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(impl.description, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "$percent% complete",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                    }

                    impl.goals.forEachIndexed { goalIndex, goalItem ->
                        item(key = "goal-$goalIndex") {
                            Spacer(Modifier.height(8.dp))
                            Text(goalItem.goal, style = MaterialTheme.typography.titleSmall)
                        }
                        itemsIndexed(goalItem.tasks, key = { ti, task -> task.id }) { taskIndex, task ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Checkbox(
                                    checked = task.done,
                                    onCheckedChange = { viewModel.toggleTask(goalIndex, taskIndex) },
                                )
                                Text(task.task, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        state.idea?.let { idea ->
                            OutlinedButton(
                                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(idea.url))) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Original post") }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
