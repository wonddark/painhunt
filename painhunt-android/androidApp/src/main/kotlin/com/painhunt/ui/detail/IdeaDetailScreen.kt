package com.painhunt.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownTypography
import com.painhunt.domain.ChatRole
import com.painhunt.presentation.IdeaChatUiState
import com.painhunt.presentation.IdeaChatViewModel
import com.painhunt.presentation.IdeaDetailUiState
import com.painhunt.presentation.IdeaDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeaDetailScreen(
    ideaId: String,
    viewModel: IdeaDetailViewModel,
    chatViewModel: IdeaChatViewModel,
    onBack: () -> Unit,
    onNavigateToImplementation: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(ideaId) { viewModel.load(ideaId) }
    LaunchedEffect(ideaId) { chatViewModel.load(ideaId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Idea Detail") },
                windowInsets = WindowInsets(0),
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

        if (state.error != null && state.idea == null) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Scaffold
        }

        state.idea ?: return@Scaffold

        var selectedTab by remember { mutableIntStateOf(0) }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Overview") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Notes") },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Discuss") },
                )
            }
            when (selectedTab) {
                0 -> OverviewContent(state, viewModel, onNavigateToImplementation)
                1 -> NotesContent(state, viewModel)
                2 -> ChatContent(
                    chatState = chatState,
                    onSend = { text -> chatViewModel.send(ideaId, text) },
                )
            }
        }
    }
}

@Composable
private fun OverviewContent(
    state: IdeaDetailUiState,
    viewModel: IdeaDetailViewModel,
    onNavigateToImplementation: (String) -> Unit,
) {
    val idea = state.idea ?: return

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
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
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()

        when {
            state.isStartingImplementation -> {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Generating plan...")
                }
            }
            state.isImplementing -> {
                FilledTonalButton(
                    onClick = { onNavigateToImplementation(state.implementationId!!) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("View implementation") }
            }
            else -> {
                Button(
                    onClick = viewModel::startImplementing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Implement this") }
            }
        }

        if (state.error != null) {
            Text(
                state.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun NotesContent(
    state: IdeaDetailUiState,
    viewModel: IdeaDetailViewModel,
) {
    var noteText by remember(state.note) { mutableStateOf(state.note?.content ?: "") }
    var tagsText by remember(state.note) {
        mutableStateOf(state.note?.tags?.joinToString(", ") ?: "")
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
    }
}

@Composable
private fun ChatContent(
    chatState: IdeaChatUiState,
    onSend: (String) -> Unit,
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(chatState.messages.size) {
        if (chatState.messages.isNotEmpty()) {
            listState.animateScrollToItem(chatState.messages.size - 1)
        }
    }
    LaunchedEffect(chatState.isStreaming) {
        if (chatState.isStreaming) {
            listState.animateScrollToItem(chatState.messages.size)
        }
    }
    // Keep the newest streamed line in view as tokens arrive. A large scroll
    // offset pins the streaming item to the bottom of the viewport.
    LaunchedEffect(chatState.streamingText) {
        if (chatState.isStreaming) {
            listState.scrollToItem(chatState.messages.size, Int.MAX_VALUE)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when {
                chatState.isLoadingHistory -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                chatState.messages.isEmpty() && !chatState.isStreaming -> {
                    Text(
                        "Ask anything about this idea.",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(chatState.messages, key = { it.id }) { msg ->
                            MessageBubble(msg.role, msg.content, false)
                        }
                        if (chatState.isStreaming) {
                            item(key = "streaming") {
                                MessageBubble(ChatRole.ASSISTANT, chatState.streamingText, true)
                            }
                        }
                    }
                }
            }
        }

        if (chatState.error != null) {
            Text(
                chatState.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Message...") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            IconButton(
                onClick = {
                    val text = inputText.trim()
                    if (text.isNotEmpty()) {
                        onSend(text)
                        inputText = ""
                    }
                },
                enabled = !chatState.isStreaming && inputText.isNotBlank(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun MessageBubble(role: ChatRole, content: String, streaming: Boolean) {
    val isUser = role == ChatRole.USER
    val displayText = when {
        streaming && content.isEmpty() -> "▌"
        streaming -> "$content▌"
        else -> stripLeadingTldr(content)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (isUser)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surfaceDim,
        ) {
            // Render finalized assistant messages as markdown. While streaming we
            // show plain text: re-parsing the markdown on every token causes the
            // whole tree to rebuild each frame, which makes the bubble flicker.
            if (isUser || streaming) {
                Text(
                    text = displayText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Markdown(
                    content = displayText,
                    colors = markdownColor(text = MaterialTheme.colorScheme.onSurface),
                    typography = chatMarkdownTypography(),
                    components = chatMarkdownComponents(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * Markdown typography tuned for compact chat bubbles. Maps headings to the M3
 * title/body type scale (instead of the library's display* defaults, which are
 * far too large) and keeps body text at bodyMedium to match the rest of the UI.
 */
@Composable
private fun chatMarkdownTypography(): MarkdownTypography {
    val type = MaterialTheme.typography
    return markdownTypography(
        h1 = type.titleLarge.copy(fontWeight = FontWeight.Bold),
        h2 = type.titleMedium.copy(fontWeight = FontWeight.Bold),
        h3 = type.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        h4 = type.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        h5 = type.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        h6 = type.bodySmall.copy(fontWeight = FontWeight.SemiBold),
        text = type.bodyMedium,
        paragraph = type.bodyMedium,
        ordered = type.bodyMedium,
        bullet = type.bodyMedium,
        list = type.bodyMedium,
        code = type.bodySmall.copy(fontFamily = FontFamily.Monospace),
        inlineCode = type.bodyMedium.copy(fontFamily = FontFamily.Monospace),
    )
}

/**
 * Matches a leading "TL;DR" line/paragraph (optionally as a heading or bold)
 * up to the first blank line, so the redundant summary the model sometimes
 * prepends can be dropped from the rendered answer.
 */
private val TLDR_REGEX = Regex(
    "^\\s*(?:#{1,6}\\s*)?(?:\\*\\*|__)?\\s*tl;?dr\\b.*?(?:\\n\\s*\\n|$)",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private fun stripLeadingTldr(text: String): String =
    text.replaceFirst(TLDR_REGEX, "").trimStart()

/**
 * Markdown components tuned for chat. Overrides table rendering so cells wrap
 * their content instead of clipping to a single line (the library default).
 * Columns keep the library's weight-based layout, so they share the available
 * width evenly and stay aligned across rows. Keeps the m3 checkbox default.
 */
@Composable
private fun chatMarkdownComponents(): MarkdownComponents = markdownComponents(
    checkbox = { MarkdownCheckBox(it.content, it.node, it.typography.text) },
    table = { model ->
        MarkdownTable(
            content = model.content,
            node = model.node,
            style = model.typography.table,
            headerBlock = { content, header, tableWidth, style ->
                MarkdownTableHeader(
                    content = content,
                    header = header,
                    tableWidth = tableWidth,
                    style = style,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                )
            },
            rowBlock = { content, row, tableWidth, style ->
                MarkdownTableRow(
                    content = content,
                    header = row,
                    tableWidth = tableWidth,
                    style = style,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                )
            },
        )
    },
)
