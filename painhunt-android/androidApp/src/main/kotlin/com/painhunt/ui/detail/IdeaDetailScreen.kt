package com.painhunt.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.painhunt.presentation.IdeaDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeaDetailScreen(
    ideaId: String,
    viewModel: IdeaDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(ideaId) { viewModel.load(ideaId) }

    var noteText by remember(state.note) { mutableStateOf(state.note?.content ?: "") }
    var tagsText by remember(state.note) { mutableStateOf(state.note?.tags?.joinToString(", ") ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Idea Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    state.idea?.let { idea ->
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(idea.url)))
                        }) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in Reddit")
                        }
                        IconButton(onClick = viewModel::toggleBookmark) {
                            Icon(
                                if (state.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Bookmark",
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val idea = state.idea ?: return@Scaffold

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(idea.aiCategory) })
                AssistChip(onClick = {}, label = { Text("Score ${idea.aiRelevanceScore}%") })
                AssistChip(onClick = {}, label = { Text("↑${idea.redditScore}") })
            }
            Text(idea.title, style = MaterialTheme.typography.titleLarge)
            Text(idea.aiSummary, style = MaterialTheme.typography.bodyMedium)
            idea.bodyExcerpt?.let { body ->
                if (body.isNotBlank()) {
                    HorizontalDivider()
                    Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            Text("Notes", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Your notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            OutlinedTextField(
                value = tagsText,
                onValueChange = { tagsText = it },
                label = { Text("Tags (comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val tags = tagsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    viewModel.saveNote(noteText, tags)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save Notes") }

            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
