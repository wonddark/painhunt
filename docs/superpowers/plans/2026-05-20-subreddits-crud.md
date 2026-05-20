# Subreddits CRUD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix FAB/list hidden behind bottom nav bar, and add rename functionality to the subreddits screen.

**Architecture:** Apply `innerPadding` from outer `Scaffold` in `MainActivity` to `NavHost` so inner screens lay out correctly. Add `rename` through the standard 3-layer stack: `SubredditsRepository` → `SubredditsViewModel` → `SubredditsScreen`. Edit icon per row opens a pre-filled `AlertDialog` — same pattern as existing add dialog.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), KMP shared module, Supabase Kotlin client (`io.github.jan.supabase:postgrest`)

---

### Task 1: Fix outer Scaffold padding

**Files:**
- Modify: `painhunt-android/androidApp/src/main/kotlin/com/painhunt/app/MainActivity.kt`

- [ ] **Step 1: Apply innerPadding to NavHost**

In `MainActivity.kt`, find the outer `Scaffold` content lambda (currently `{ _ ->`). Change it to:

```kotlin
} { innerPadding ->
    NavHost(
        navController,
        startDestination = FeedRoute,
        modifier = Modifier.padding(innerPadding)
    ) {
```

You'll need to add `import androidx.compose.ui.Modifier` and `import androidx.compose.foundation.layout.padding` if not already present. The full `setContent` block after the change:

```kotlin
setContent {
    PainHuntTheme {
        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = backStack?.destination?.hasRoute(FeedRoute::class) == true,
                        onClick = { navController.navigate(FeedRoute) { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("Feed") },
                    )
                    NavigationBarItem(
                        selected = backStack?.destination?.hasRoute(SubredditsRoute::class) == true,
                        onClick = { navController.navigate(SubredditsRoute) { launchSingleTop = true } },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                        label = { Text("Subreddits") },
                    )
                    NavigationBarItem(
                        selected = backStack?.destination?.hasRoute(SettingsRoute::class) == true,
                        onClick = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text("Settings") },
                    )
                }
            }
        ) { innerPadding ->
            NavHost(
                navController,
                startDestination = FeedRoute,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable<FeedRoute> {
                    val vm = viewModel { FeedViewModel(ideasRepo, settingsRepo) }
                    FeedScreen(vm) { ideaId -> navController.navigate(DetailRoute(ideaId)) }
                }
                composable<DetailRoute> { entry ->
                    val route = entry.toRoute<DetailRoute>()
                    val vm = viewModel { IdeaDetailViewModel(ideasRepo, bookmarksRepo) }
                    IdeaDetailScreen(route.ideaId, vm) { navController.popBackStack() }
                }
                composable<SubredditsRoute> {
                    val vm = viewModel { SubredditsViewModel(subredditsRepo) }
                    SubredditsScreen(vm)
                }
                composable<SettingsRoute> {
                    val vm = viewModel { SettingsViewModel(settingsRepo) }
                    SettingsScreen(vm)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build and verify**

```bash
cd painhunt-android && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL. Install on device/emulator — FAB visible on Subreddits screen, list items no longer cut off by nav bar.

- [ ] **Step 3: Commit**

```bash
git add painhunt-android/androidApp/src/main/kotlin/com/painhunt/app/MainActivity.kt
git commit -m "fix(ui): apply innerPadding to NavHost so FAB and list aren't hidden behind nav bar"
```

---

### Task 2: Add rename to SubredditsRepository

**Files:**
- Modify: `painhunt-android/shared/src/commonMain/kotlin/com/painhunt/data/SubredditsRepository.kt`

- [ ] **Step 1: Add NameUpdate data class and rename function**

Open `SubredditsRepository.kt`. Add a `NameUpdate` serializable class alongside the existing private classes, then add the `rename` suspend function. Full file after change:

```kotlin
package com.painhunt.data

import com.painhunt.domain.Subreddit
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class SubredditsRepository(private val client: SupabaseClient) {

    @Serializable
    private data class SubredditInsert(val name: String)

    @Serializable
    private data class ActiveUpdate(val active: Boolean)

    @Serializable
    private data class NameUpdate(val name: String)

    suspend fun getAll(): List<Subreddit> =
        client.from("subreddits").select {
            order("added_at", Order.ASCENDING)
        }.decodeList()

    suspend fun add(name: String) {
        client.from("subreddits").insert(SubredditInsert(name.removePrefix("r/").trim()))
    }

    suspend fun remove(id: String) {
        client.from("subreddits").delete {
            filter { eq("id", id) }
        }
    }

    suspend fun setActive(id: String, active: Boolean) {
        client.from("subreddits").update(ActiveUpdate(active)) {
            filter { eq("id", id) }
        }
    }

    suspend fun rename(id: String, name: String) {
        client.from("subreddits").update(NameUpdate(name.removePrefix("r/").trim())) {
            filter { eq("id", id) }
        }
    }
}
```

- [ ] **Step 2: Build shared module**

```bash
cd painhunt-android && ./gradlew :shared:compileCommonMainKotlinMetadata
```

Expected: BUILD SUCCESSFUL with no errors.

- [ ] **Step 3: Commit**

```bash
git add painhunt-android/shared/src/commonMain/kotlin/com/painhunt/data/SubredditsRepository.kt
git commit -m "feat(data): add rename to SubredditsRepository"
```

---

### Task 3: Add rename to SubredditsViewModel

**Files:**
- Modify: `painhunt-android/shared/src/commonMain/kotlin/com/painhunt/presentation/SubredditsViewModel.kt`

- [ ] **Step 1: Add rename function**

Open `SubredditsViewModel.kt`. Add `rename` after `setActive`. Full file after change:

```kotlin
package com.painhunt.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.painhunt.data.SubredditsRepository
import com.painhunt.domain.Subreddit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SubredditsUiState(
    val subreddits: List<Subreddit> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class SubredditsViewModel(
    private val subredditsRepository: SubredditsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubredditsUiState())
    val uiState: StateFlow<SubredditsUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                _uiState.update { it.copy(subreddits = subredditsRepository.getAll(), isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun add(name: String) {
        viewModelScope.launch {
            try {
                subredditsRepository.add(name)
                load()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun remove(id: String) {
        viewModelScope.launch {
            try {
                subredditsRepository.remove(id)
                load()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun setActive(id: String, active: Boolean) {
        viewModelScope.launch {
            try {
                subredditsRepository.setActive(id, active)
                load()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch {
            try {
                subredditsRepository.rename(id, name)
                load()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
```

- [ ] **Step 2: Build shared module**

```bash
cd painhunt-android && ./gradlew :shared:compileCommonMainKotlinMetadata
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add painhunt-android/shared/src/commonMain/kotlin/com/painhunt/presentation/SubredditsViewModel.kt
git commit -m "feat(presentation): add rename to SubredditsViewModel"
```

---

### Task 4: Add edit icon and dialog to SubredditsScreen

**Files:**
- Modify: `painhunt-android/androidApp/src/main/kotlin/com/painhunt/ui/subreddits/SubredditsScreen.kt`

- [ ] **Step 1: Update SubredditsScreen with edit state and dialog**

Replace the full contents of `SubredditsScreen.kt`:

```kotlin
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
                    label = { Text("Subreddit name") },
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
```

- [ ] **Step 2: Build full app**

```bash
cd painhunt-android && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual verification**

Install on device/emulator and verify:
1. Subreddits screen shows FAB (no longer hidden behind nav bar)
2. Tapping FAB opens "Add Subreddit" dialog → add works
3. Each row shows Switch, pencil icon, delete icon
4. Tapping pencil opens "Edit Subreddit" dialog pre-filled with current name → save renames row
5. Tapping delete removes row
6. Switch toggles active state

- [ ] **Step 4: Commit**

```bash
git add painhunt-android/androidApp/src/main/kotlin/com/painhunt/ui/subreddits/SubredditsScreen.kt
git commit -m "feat(ui): add edit icon and rename dialog to SubredditsScreen"
```
