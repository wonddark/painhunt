package com.painhunt.ui.feed

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.painhunt.data.SortField
import com.painhunt.presentation.FeedViewModel

private val CATEGORIES = listOf(null, "SaaS", "Mobile", "Hardware", "Service", "Other")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    onIdeaClick: (String) -> Unit,
    onSources: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) viewModel.uploadFile(context, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PainHunt") },
                actions = {
                    IconButton(
                        onClick = viewModel::triggerScrape,
                        enabled = !state.isScraping && !state.isUploading,
                    ) {
                        if (state.isScraping) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Scrape")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sort", style = MaterialTheme.typography.labelSmall) },
                                onClick = {},
                                enabled = false,
                            )
                            DropdownMenuItem(
                                text = { Text("Newest") },
                                onClick = {
                                    viewModel.setSortBy(SortField.ScrapedAt)
                                    menuExpanded = false
                                },
                                leadingIcon = if (state.sortBy == SortField.ScrapedAt) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null,
                            )
                            DropdownMenuItem(
                                text = { Text("Top") },
                                onClick = {
                                    viewModel.setSortBy(SortField.Relevance)
                                    menuExpanded = false
                                },
                                leadingIcon = if (state.sortBy == SortField.Relevance) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null,
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Sources") },
                                onClick = {
                                    onSources()
                                    menuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    onSettings()
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CATEGORIES.forEach { cat ->
                    FilterChip(
                        selected = state.selectedCategory == cat,
                        onClick = { viewModel.setCategory(cat) },
                        label = { Text(cat ?: "All") },
                    )
                }
            }

            OutlinedButton(
                onClick = { fileLauncher.launch("application/json") },
                enabled = !state.isUploading && !state.isScraping,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                if (state.isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Import File")
            }

            if (state.error != null) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    IconButton(onClick = viewModel::clearError) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss error", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.ideas.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No ideas yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = viewModel::triggerScrape, enabled = !state.isScraping && !state.isUploading) { Text("Trigger Scrape") }
                    }
                }
            } else {
                LazyColumn {
                    items(state.ideas, key = { it.id }) { idea ->
                        IdeaCard(idea = idea, onClick = { onIdeaClick(idea.id) })
                    }
                }
            }
        }
    }
}
