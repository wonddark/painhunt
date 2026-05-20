package com.painhunt.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.painhunt.presentation.SettingsViewModel
import kotlinx.coroutines.delay

private val OLLAMA_MODELS = listOf("llama3.2", "llama3.1", "mistral", "phi3", "gemma2")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val settings = state.settings

    var model by remember(settings) { mutableStateOf(settings?.ollamaModel ?: "llama3.2") }
    var minUpvotes by remember(settings) { mutableStateOf(settings?.minUpvotesThreshold?.toString() ?: "10") }
    var apiKey by remember(settings) { mutableStateOf(settings?.ollamaApiKey ?: "") }
    var scraperUrl by remember(settings) { mutableStateOf(settings?.scraperBaseUrl ?: "http://localhost:3000") }
    var modelDropdownOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            delay(2000)
            viewModel.clearSaved()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        if (state.isLoading && settings == null) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Ollama Cloud", style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Ollama API Key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )

            ExposedDropdownMenuBox(
                expanded = modelDropdownOpen,
                onExpandedChange = { modelDropdownOpen = it },
            ) {
                OutlinedTextField(
                    value = model,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Model") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = modelDropdownOpen,
                    onDismissRequest = { modelDropdownOpen = false },
                ) {
                    OLLAMA_MODELS.forEach { m ->
                        DropdownMenuItem(text = { Text(m) }, onClick = { model = m; modelDropdownOpen = false })
                    }
                }
            }

            OutlinedTextField(
                value = scraperUrl,
                onValueChange = { scraperUrl = it },
                label = { Text("Scraper Base URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            HorizontalDivider()
            Text("Filtering", style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = minUpvotes,
                onValueChange = { minUpvotes = it },
                label = { Text("Minimum upvotes") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            Button(
                onClick = {
                    val upvotes = minUpvotes.toIntOrNull() ?: 10
                    viewModel.save(model, upvotes, apiKey, scraperUrl)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save Settings") }

            if (state.isSaved) {
                Text("Saved!", color = MaterialTheme.colorScheme.primary)
            }

            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
