# Feed Top Bar Menu Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move Subreddits, Settings, and sort controls from bottom nav / filter chips into a MoreVert overflow menu on FeedScreen's TopAppBar.

**Architecture:** Two-file change. `MainActivity.kt` drops two nav items and passes nav lambdas down. `FeedScreen.kt` gains two nav params, a MoreVert dropdown menu, and loses the sort FilterChip row.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Navigation Compose

---

## Files

| Action | Path |
|--------|------|
| Modify | `painhunt-android/androidApp/src/main/kotlin/com/painhunt/app/MainActivity.kt` |
| Modify | `painhunt-android/androidApp/src/main/kotlin/com/painhunt/ui/feed/FeedScreen.kt` |

---

### Task 1: Update FeedScreen signature and add overflow menu

**Files:**
- Modify: `painhunt-android/androidApp/src/main/kotlin/com/painhunt/ui/feed/FeedScreen.kt`

- [ ] **Step 1: Add missing imports**

In `FeedScreen.kt`, add these two imports alongside the existing `filled.*` imports:

```kotlin
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
```

- [ ] **Step 2: Update function signature**

Replace:
```kotlin
fun FeedScreen(viewModel: FeedViewModel, onIdeaClick: (String) -> Unit) {
```
With:
```kotlin
fun FeedScreen(
    viewModel: FeedViewModel,
    onIdeaClick: (String) -> Unit,
    onSubreddits: () -> Unit,
    onSettings: () -> Unit,
) {
```

- [ ] **Step 3: Add menuExpanded state**

After `val context = LocalContext.current`, add:
```kotlin
var menuExpanded by remember { mutableStateOf(false) }
```

- [ ] **Step 4: Replace TopAppBar actions**

Replace the entire `actions = { ... }` block inside `TopAppBar` with:

```kotlin
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
                text = { Text("Subreddits") },
                onClick = {
                    onSubreddits()
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
```

- [ ] **Step 5: Remove sort FilterChip row**

Delete these lines from the `Column` body (the sort chips, NOT the category chips):

```kotlin
Row(
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    FilterChip(
        selected = state.sortBy == SortField.ScrapedAt,
        onClick = { viewModel.setSortBy(SortField.ScrapedAt) },
        label = { Text("Newest") },
    )
    FilterChip(
        selected = state.sortBy == SortField.Relevance,
        onClick = { viewModel.setSortBy(SortField.Relevance) },
        label = { Text("Top") },
    )
}
```

The category `FilterChip` row below it stays untouched.

- [ ] **Step 6: Commit**

```bash
git add painhunt-android/androidApp/src/main/kotlin/com/painhunt/ui/feed/FeedScreen.kt
git commit -m "feat(feed): add overflow menu with sort, subreddits, settings"
```

---

### Task 2: Update MainActivity to wire nav lambdas and trim bottom bar

**Files:**
- Modify: `painhunt-android/androidApp/src/main/kotlin/com/painhunt/app/MainActivity.kt`

- [ ] **Step 1: Remove unused imports**

Delete these two import lines:

```kotlin
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
```

- [ ] **Step 2: Remove Subreddits and Settings NavigationBarItems**

Inside the `NavigationBar { ... }` block, delete both items, leaving only the Feed item:

```kotlin
NavigationBar {
    NavigationBarItem(
        selected = backStack?.destination?.hasRoute(FeedRoute::class) == true,
        onClick = { navController.navigate(FeedRoute) { launchSingleTop = true } },
        icon = { Icon(Icons.Default.Home, null) },
        label = { Text("Feed") },
    )
}
```

- [ ] **Step 3: Update FeedScreen call to pass nav lambdas**

Replace:
```kotlin
FeedScreen(vm) { ideaId -> navController.navigate(DetailRoute(ideaId)) }
```
With:
```kotlin
FeedScreen(
    viewModel = vm,
    onIdeaClick = { ideaId -> navController.navigate(DetailRoute(ideaId)) },
    onSubreddits = { navController.navigate(SubredditsRoute) { launchSingleTop = true } },
    onSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
)
```

- [ ] **Step 4: Commit**

```bash
git add painhunt-android/androidApp/src/main/kotlin/com/painhunt/app/MainActivity.kt
git commit -m "feat(nav): move subreddits and settings to feed overflow menu"
```
