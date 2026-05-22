# Feed Top Bar Menu

## Goal

Move Subreddits and Settings out of bottom navigation bar into an overflow menu on FeedScreen's TopAppBar. Move sort controls (Newest / Top) into the same menu. Keep bottom NavigationBar for a future screen.

## Scope

Two files changed: `MainActivity.kt`, `FeedScreen.kt`. No other screens touched.

## Navigation Structure

Routes unchanged: `FeedRoute`, `SubredditsRoute`, `SettingsRoute`, `DetailRoute` all remain as full nav destinations.

Bottom `NavigationBar` keeps its `FeedRoute` item. Subreddits and Settings `NavigationBarItem`s removed. Bar itself stays.

## FeedScreen TopAppBar

Actions (left to right):
1. Refresh `IconButton` — existing, unchanged
2. `MoreVert` `IconButton` — new, toggles `menuExpanded`

`DropdownMenu` anchored to MoreVert button, controlled by `var menuExpanded by remember { mutableStateOf(false) }`.

Menu items in order:

| Item | Behaviour |
|------|-----------|
| "Sort" (disabled label) | Section header, not clickable |
| "Newest" | Calls `viewModel.setSortBy(SortField.ScrapedAt)`, closes menu. Leading `Check` icon when `state.sortBy == SortField.ScrapedAt` |
| "Top" | Calls `viewModel.setSortBy(SortField.Relevance)`, closes menu. Leading `Check` icon when `state.sortBy == SortField.Relevance` |
| `HorizontalDivider` | Visual separator |
| "Subreddits" | Calls `onSubreddits()`, closes menu |
| "Settings" | Calls `onSettings()`, closes menu |

## FeedScreen Signature Change

```kotlin
fun FeedScreen(
    viewModel: FeedViewModel,
    onIdeaClick: (String) -> Unit,
    onSubreddits: () -> Unit,
    onSettings: () -> Unit,
)
```

## Removed from FeedScreen Body

Sort `FilterChip` row (current lines 55–69) removed entirely. Category chip row stays.

## MainActivity Changes

- Remove `SubredditsRoute` and `SettingsRoute` `NavigationBarItem`s from `NavigationBar`
- Remove unused imports: `Icons.AutoMirrored.Filled.List`, `Icons.Default.Settings`
- Pass lambdas to `FeedScreen`:
  - `onSubreddits = { navController.navigate(SubredditsRoute) { launchSingleTop = true } }`
  - `onSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } }`
