# Subreddits CRUD — Design

## Problem

FAB & bottom list content hidden behind bottom nav bar. No rename capability.

## Scope

- Fix outer `Scaffold` padding in `MainActivity`
- Add rename (edit) to subreddits screen

Out of scope: delete confirmation dialog, reorder, bulk actions.

---

## Architecture

### Bug Fix

`MainActivity` outer `Scaffold` passes `{ _ ->` (discards inner padding), so `NavHost`
content fills full screen — FAB and bottom list items overlap with nav bar.

Fix: apply `innerPadding` to `NavHost`:

```kotlin
} { innerPadding ->
    NavHost(navController, startDestination = FeedRoute, modifier = Modifier.padding(innerPadding)) {
```

### Rename Feature

**`SubredditsRepository`** — new `suspend fun rename(id: String, name: String)`:
- Updates `name` column where `id` matches
- Strips `r/` prefix + trims (same as `add`)

**`SubredditsViewModel`** — new `fun rename(id: String, name: String)`:
- Calls `subredditsRepository.rename(id, name)`
- Reloads list on success
- Updates `error` on exception — same pattern as `add`/`remove`/`setActive`

**`SubredditsScreen`** — UI changes:
- New state: `var editingSubreddit by remember { mutableStateOf<Subreddit?>(null) }`
- Row trailing content order: `[Switch] [EditIcon] [DeleteIcon]`
- Edit icon: `Icons.Default.Edit`, sets `editingSubreddit = subreddit`
- Edit `AlertDialog` (mirrors add dialog):
  - Title: "Edit Subreddit"
  - Text field seeded with `editingSubreddit!!.name`
  - Confirm: calls `viewModel.rename(editingSubreddit!!.id, newName)`, clears state
  - Dismiss: clears `editingSubreddit`

---

## Data Flow

```
EditIcon tap
  → editingSubreddit = subreddit
  → AlertDialog opens pre-filled
  → user edits name → confirm
  → viewModel.rename(id, name)
  → SubredditsRepository.rename(id, name)
  → Supabase UPDATE subreddits SET name=? WHERE id=?
  → load() → UI refresh
```

---

## Error Handling

Errors surface via existing `SubredditsUiState.error` string → shown in error banner with Retry button. No new error paths needed.

---

## Files Changed

| File | Change |
|------|--------|
| `MainActivity.kt` | Apply `innerPadding` to `NavHost` |
| `SubredditsRepository.kt` | Add `rename(id, name)` |
| `SubredditsViewModel.kt` | Add `rename(id, name)` |
| `SubredditsScreen.kt` | Add edit icon, edit dialog, `editingSubreddit` state |

No new files. No new routes.
