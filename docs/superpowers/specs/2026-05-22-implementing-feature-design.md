# Implementing Feature Design

## Goal

Allow users to pick an idea to implement. Creates a persistent implementation plan per idea — AI-generated concept, description, measurable goals, and tasks per goal. Users mark tasks done; the system tracks completion percent. One implementation per idea (1:1).

## Architecture

- **Trigger:** IdeaDetailScreen "Implement this" button → `POST /implement` on scraper backend → AI generates plan → stored in Supabase → app reads back and navigates to detail
- **Task completion:** App fetches full implementation, toggles task locally, pushes updated JSONB back to Supabase
- **Percent:** Computed client-side: `done / total * 100`
- **Removal:** User can abandon — deletes row, navigates back

---

## Section 1: Database

New migration file: `supabase/migrations/002_implementations.sql`

```sql
CREATE TABLE implementations (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  idea_id      uuid NOT NULL UNIQUE REFERENCES ideas(id) ON DELETE CASCADE,
  concept      text NOT NULL,
  description  text NOT NULL,
  goals        jsonb NOT NULL DEFAULT '[]',
  created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX implementations_created_at_idx ON implementations(created_at DESC);
```

### goals JSONB shape

```json
[
  {
    "goal": "User authentication",
    "tasks": [
      { "id": "<uuid>", "task": "Design login flow", "done": false },
      { "id": "<uuid>", "task": "Implement JWT handling", "done": false }
    ]
  }
]
```

`original_url` is not duplicated — fetched from `ideas.url` via `idea_id`.

---

## Section 2: Scraper Backend

### New files

| File | Purpose |
|------|---------|
| `src/ai/implementationPlanner.ts` | AI prompt + response parsing |
| `src/db/implementations.ts` | Supabase insert + conflict handling |

### New endpoint

`POST /implement` added to `src/api/routes.ts`

**Request body:**
```json
{ "ideaId": "uuid", "title": "...", "summary": "...", "bodyExcerpt": "..." }
```

**Flow:**
1. Fetch settings from DB (`getSettings()`)
2. Call Ollama via `planImplementation(title, summary, bodyExcerpt, settings)`
3. Parse + validate AI response
4. Insert into `implementations` (conflict on `idea_id` → return existing row)
5. Return `{ id, ideaId, concept, description, goals }`

**Error responses:**
- `400` — missing required fields
- `500` — AI call failed or DB error

### AI prompt (`implementationPlanner.ts`)

```
You are a product and engineering planner.

Idea title: "${title}"
AI summary: "${summary}"
Original post excerpt: "${bodyExcerpt}"

Generate a compact implementation plan. Respond ONLY with valid JSON:
{
  "concept": "<one sentence — the core thing being built>",
  "description": "<2-3 sentences on what needs to be done to solve the problem>",
  "goals": [
    {
      "goal": "<measurable goal name>",
      "tasks": [
        { "id": "<uuid>", "task": "<specific measurable task>", "done": false }
      ]
    }
  ]
}

Rules: 2-4 goals. 2-5 tasks per goal. Tasks must be specific and measurable.
```

**Parsing:** Strip markdown fences → `JSON.parse` → validate `concept` (string), `description` (string), `goals` (array, each with `goal` string and `tasks` array, each task with `id`, `task`, `done`). Same pattern as `src/ai/ollama.ts`.

---

## Section 3: Data Layer (Shared KMP)

### New domain models

**`domain/ImplementationTask.kt`**
```kotlin
@Serializable
data class ImplementationTask(val id: String, val task: String, val done: Boolean)
```

**`domain/ImplementationGoal.kt`**
```kotlin
@Serializable
data class ImplementationGoal(val goal: String, val tasks: List<ImplementationTask>)
```

**`domain/Implementation.kt`**
```kotlin
@Serializable
data class Implementation(
    val id: String,
    @SerialName("idea_id") val ideaId: String,
    val concept: String,
    val description: String,
    val goals: List<ImplementationGoal>,
    @SerialName("created_at") val createdAt: String,
)
```

### New `data/ImplementationsRepository.kt`

| Method | Description |
|--------|-------------|
| `getAll(): List<Implementation>` | All rows, ordered by `created_at DESC` |
| `getByIdeaId(ideaId: String): Implementation?` | Null if idea not yet implementing |
| `updateGoals(id: String, goals: List<ImplementationGoal>)` | Full JSONB replace after task toggle |
| `remove(id: String)` | Delete row |

Create path runs through scraper endpoint — app calls `POST /implement`, then reads back via `getByIdeaId()`.

---

## Section 4: Presentation Layer

### `ImplementingListViewModel`

```kotlin
data class ImplementingListUiState(
    val implementations: List<Implementation> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
```

Methods: `load()`, `remove(id: String)`

### `ImplementingDetailViewModel`

```kotlin
data class ImplementingDetailUiState(
    val implementation: Implementation? = null,
    val idea: Idea? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val removed: Boolean = false,
    val error: String? = null,
)
```

Methods:
- `load(implementationId: String)` — fetches `Implementation` + its `Idea`
- `toggleTask(goalIndex: Int, taskIndex: Int)` — flips `done`, calls `updateGoals()`
- `remove()` — deletes row, sets `removed = true`; screen observes `removed` and calls `popBackStack()`

### `IdeaDetailViewModel` additions

New state fields:
- `isImplementing: Boolean` — true if `getByIdeaId(ideaId)` returns non-null
- `implementationId: String?` — id of existing implementation if present
- `isStartingImplementation: Boolean` — true while scraper call in flight

New dependency: `settingsRepo: SettingsRepository` added to `IdeaDetailViewModel` constructor (same pattern as `FeedViewModel` — fetches `scraperBaseUrl` at call time).

New method:
- `startImplementing()` — fetches `scraperBaseUrl` from `settingsRepo`, POSTs to `$scraperBaseUrl/implement` with idea fields, on success sets `isImplementing = true` and `implementationId`

---

## Section 5: UI & Navigation

### New routes

```kotlin
@Serializable object ImplementingRoute
@Serializable data class ImplementingDetailRoute(val implementationId: String)
```

### Bottom bar

Add `ImplementingRoute` item to `NavigationBar` in `MainActivity`:
- Icon: `Icons.Default.Build` (hammer/wrench — work in progress)
- Label: "Implementing"

### `ImplementingListScreen`

- Each card: concept (title style), `LinearProgressIndicator` + "X% complete", no category chip needed
- Tap → navigate to `ImplementingDetailRoute(implementation.id)`
- Empty state: "No ideas being implemented yet"
- Back button: none (bottom bar screen)

### `ImplementingDetailScreen`

Layout (top to bottom):
- `TopAppBar` with back arrow (`navigationIcon`) + "Abandon" action (icon: `Icons.Default.Delete`, confirm via `AlertDialog` before deleting)
- Concept (headline text)
- Description (body text)
- `LinearProgressIndicator` + "X% complete" label
- Goals: each goal is a `Text` section header, tasks are `Checkbox` rows
- Footer: "Original post" `OutlinedButton` → opens `idea.url` in browser

### `IdeaDetailScreen` additions

- Load: call `loadImplementationStatus()` on VM init
- If `!state.isImplementing`: show `Button("Implement this")` — disabled + `CircularProgressIndicator` while `isStartingImplementation`
- If `state.isImplementing`: show `FilledTonalButton("View implementation")` → navigates to `ImplementingDetailRoute(state.implementationId!!)`

### Navigation wiring (`MainActivity`)

New params on `IdeaDetailScreen`:
- `onNavigateToImplementation: (implementationId: String) -> Unit`

New composable destinations:
```kotlin
composable<ImplementingRoute> {
    val vm = viewModel { ImplementingListViewModel(implementationsRepo) }
    ImplementingListScreen(vm) { id -> navController.navigate(ImplementingDetailRoute(id)) }
}
composable<ImplementingDetailRoute> { entry ->
    val route = entry.toRoute<ImplementingDetailRoute>()
    val vm = viewModel { ImplementingDetailViewModel(implementationsRepo, ideasRepo) }
    ImplementingDetailScreen(route.implementationId, vm) { navController.popBackStack() }
}
```

New repo instantiation:
```kotlin
val implementationsRepo = ImplementationsRepository(supabase)
```
