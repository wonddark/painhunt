# Implementing Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an "Implementing" feature that lets users pick an idea from the detail screen, generate an AI implementation plan (concept, description, measurable goals + tasks), track task completion, and manage active implementations from a dedicated bottom-bar screen.

**Architecture:** AI plan generation runs through a new `POST /implement` scraper endpoint; the generated plan (JSONB goals column) is stored in a new `implementations` Supabase table with a 1:1 FK to `ideas`. Android app reads/writes via a new `ImplementationsRepository`; task completion toggles fetch the full row, flip the boolean locally, and push the updated JSONB back. Percent is computed client-side.

**Tech Stack:** Kotlin/Compose (Android), Ktor (HTTP), Supabase-kt (PostgREST), kotlinx.serialization, TypeScript/Express (scraper), Ollama (AI), Vitest (scraper tests)

---

## File Map

| Action | Path |
|--------|------|
| Create | `supabase/migrations/002_implementations.sql` |
| Create | `painhunt-scraper/src/ai/implementationPlanner.ts` |
| Create | `painhunt-scraper/tests/ai/implementationPlanner.test.ts` |
| Create | `painhunt-scraper/src/db/implementations.ts` |
| Modify | `painhunt-scraper/src/api/routes.ts` |
| Create | `painhunt-android/shared/src/commonMain/kotlin/com/painhunt/domain/ImplementationTask.kt` |
| Create | `painhunt-android/shared/src/commonMain/kotlin/com/painhunt/domain/ImplementationGoal.kt` |
| Create | `painhunt-android/shared/src/commonMain/kotlin/com/painhunt/domain/Implementation.kt` |
| Create | `painhunt-android/shared/src/commonMain/kotlin/com/painhunt/data/ImplementationsRepository.kt` |
| Create | `painhunt-android/shared/src/commonMain/kotlin/com/painhunt/presentation/ImplementingListViewModel.kt` |
| Create | `painhunt-android/shared/src/commonMain/kotlin/com/painhunt/presentation/ImplementingDetailViewModel.kt` |
| Modify | `painhunt-android/shared/src/commonMain/kotlin/com/painhunt/presentation/IdeaDetailViewModel.kt` |
| Create | `painhunt-android/androidApp/src/main/kotlin/com/painhunt/ui/implementing/ImplementingListScreen.kt` |
| Create | `painhunt-android/androidApp/src/main/kotlin/com/painhunt/ui/implementing/ImplementingDetailScreen.kt` |
| Modify | `painhunt-android/androidApp/src/main/kotlin/com/painhunt/ui/detail/IdeaDetailScreen.kt` |
| Modify | `painhunt-android/androidApp/src/main/kotlin/com/painhunt/app/MainActivity.kt` |

---

### Task 1: DB Migration

**Files:**
- Create: `supabase/migrations/002_implementations.sql`

- [ ] **Step 1: Create migration file**

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

- [ ] **Step 2: Apply migration**

Run in Supabase SQL editor or via CLI:
```bash
supabase db push
```

Or paste directly into the Supabase dashboard SQL editor and click Run.

- [ ] **Step 3: Commit**

```bash
git add supabase/migrations/002_implementations.sql
git commit -m "feat(db): add implementations table"
```

---

### Task 2: Scraper — AI Implementation Planner

**Files:**
- Create: `painhunt-scraper/src/ai/implementationPlanner.ts`
- Create: `painhunt-scraper/tests/ai/implementationPlanner.test.ts`

- [ ] **Step 1: Write the failing test**

Create `painhunt-scraper/tests/ai/implementationPlanner.test.ts`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { planImplementation } from '../../src/ai/implementationPlanner.js'

vi.stubGlobal('fetch', vi.fn())

const validGoals = [
  {
    goal: 'Core backend',
    tasks: [
      { id: 'uuid-1', task: 'Set up database schema', done: false },
      { id: 'uuid-2', task: 'Build REST API endpoints', done: false },
    ],
  },
]

const validAiResponse = {
  message: {
    content: JSON.stringify({
      concept: 'An app that tracks expenses automatically.',
      description: 'Build a mobile app that reads bank SMS and categorises spending. Users get a weekly summary.',
      goals: validGoals,
    }),
  },
}

describe('planImplementation', () => {
  beforeEach(() => vi.mocked(fetch).mockReset())

  it('returns parsed plan for valid AI response', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: async () => validAiResponse,
    } as Response)

    const result = await planImplementation({
      title: 'Hate tracking expenses manually',
      summary: 'Users want automatic expense tracking.',
      bodyExcerpt: 'I spend an hour every week on this.',
      model: 'llama3.2',
      apiKey: 'key',
      baseUrl: 'https://api.ollama.com',
    })

    expect(result).toEqual({
      concept: 'An app that tracks expenses automatically.',
      description: 'Build a mobile app that reads bank SMS and categorises spending. Users get a weekly summary.',
      goals: validGoals,
    })
  })

  it('strips markdown fences from AI response', async () => {
    const wrapped = { message: { content: '```json\n' + JSON.stringify({ concept: 'c', description: 'd', goals: validGoals }) + '\n```' } }
    vi.mocked(fetch).mockResolvedValueOnce({ ok: true, json: async () => wrapped } as Response)

    const result = await planImplementation({ title: 't', summary: 's', bodyExcerpt: 'b', model: 'm', apiKey: 'k', baseUrl: 'u' })

    expect(result?.concept).toBe('c')
  })

  it('returns null for invalid JSON', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ message: { content: 'not json at all' } }),
    } as Response)

    const result = await planImplementation({ title: 't', summary: 's', bodyExcerpt: 'b', model: 'm', apiKey: 'k', baseUrl: 'u' })

    expect(result).toBeNull()
  })

  it('returns null when required fields are missing', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ message: { content: JSON.stringify({ concept: 'c' }) } }),
    } as Response)

    const result = await planImplementation({ title: 't', summary: 's', bodyExcerpt: 'b', model: 'm', apiKey: 'k', baseUrl: 'u' })

    expect(result).toBeNull()
  })

  it('returns null on fetch error', async () => {
    vi.mocked(fetch).mockRejectedValueOnce(new Error('Network error'))

    const result = await planImplementation({ title: 't', summary: 's', bodyExcerpt: 'b', model: 'm', apiKey: 'k', baseUrl: 'u' })

    expect(result).toBeNull()
  })

  it('returns null when Ollama returns non-ok status', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({ ok: false, status: 503 } as Response)

    const result = await planImplementation({ title: 't', summary: 's', bodyExcerpt: 'b', model: 'm', apiKey: 'k', baseUrl: 'u' })

    expect(result).toBeNull()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd painhunt-scraper && npm test -- tests/ai/implementationPlanner.test.ts
```

Expected: FAIL — `planImplementation` not found.

- [ ] **Step 3: Create `src/ai/implementationPlanner.ts`**

```typescript
export type PlanInput = {
  title: string
  summary: string
  bodyExcerpt: string
  model: string
  apiKey: string
  baseUrl: string
}

export type PlanTask = {
  id: string
  task: string
  done: boolean
}

export type PlanGoal = {
  goal: string
  tasks: PlanTask[]
}

export type PlanResult = {
  concept: string
  description: string
  goals: PlanGoal[]
}

const PROMPT = (title: string, summary: string, bodyExcerpt: string) => `
You are a product and engineering planner.

Idea title: "${title}"
AI summary: "${summary}"
Original post excerpt: "${bodyExcerpt.slice(0, 500)}"

Generate a compact implementation plan. Respond ONLY with valid JSON in this exact format:
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

Rules: 2-4 goals. 2-5 tasks per goal. Tasks must be specific and measurable. No vague tasks like "research" or "think about". Generate real UUIDs for each task id.
`.trim()

export async function planImplementation(input: PlanInput): Promise<PlanResult | null> {
  try {
    const res = await fetch(`${input.baseUrl}/api/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${input.apiKey}`,
      },
      body: JSON.stringify({
        model: input.model,
        messages: [{ role: 'user', content: PROMPT(input.title, input.summary, input.bodyExcerpt) }],
        stream: false,
        format: 'json',
      }),
    })

    if (!res.ok) {
      console.warn(`Ollama API error: ${res.status}`)
      return null
    }

    const data = (await res.json()) as { message: { content: string } }
    const raw = data.message.content
      .replace(/^```(?:json)?\s*/i, '')
      .replace(/```\s*$/i, '')
      .trim()
    const parsed = JSON.parse(raw) as PlanResult

    if (
      typeof parsed.concept !== 'string' ||
      typeof parsed.description !== 'string' ||
      !Array.isArray(parsed.goals)
    ) {
      return null
    }

    return parsed
  } catch (e) {
    console.error(e)
    return null
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd painhunt-scraper && npm test -- tests/ai/implementationPlanner.test.ts
```

Expected: All 6 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add painhunt-scraper/src/ai/implementationPlanner.ts painhunt-scraper/tests/ai/implementationPlanner.test.ts
git commit -m "feat(scraper): add AI implementation planner"
```

---

### Task 3: Scraper — Implementations DB Layer

**Files:**
- Create: `painhunt-scraper/src/db/implementations.ts`

- [ ] **Step 1: Create `src/db/implementations.ts`**

This file mirrors the pattern in `src/db/supabase.ts`. It imports the same Supabase client.

```typescript
import { createClient } from '@supabase/supabase-js'
import type { PlanGoal } from '../ai/implementationPlanner.js'

const client = createClient(
  process.env.SUPABASE_URL!,
  process.env.SUPABASE_SERVICE_ROLE_KEY!
)

export type ImplementationInsert = {
  idea_id: string
  concept: string
  description: string
  goals: PlanGoal[]
}

export type Implementation = ImplementationInsert & {
  id: string
  created_at: string
}

function throwDbError(error: { message: string }): never {
  throw new Error(error.message)
}

export async function createImplementation(data: ImplementationInsert): Promise<Implementation> {
  const { data: existing } = await client
    .from('implementations')
    .select()
    .eq('idea_id', data.idea_id)
    .maybeSingle()

  if (existing) return existing as Implementation

  const { data: inserted, error } = await client
    .from('implementations')
    .insert(data)
    .select()
    .single()

  if (error) throwDbError(error)
  return inserted as Implementation
}
```

- [ ] **Step 2: Commit**

```bash
git add painhunt-scraper/src/db/implementations.ts
git commit -m "feat(scraper): add implementations DB layer"
```

---

### Task 4: Scraper — POST /implement Route

**Files:**
- Modify: `painhunt-scraper/src/api/routes.ts`

- [ ] **Step 1: Add the route**

Add these imports at the top of `src/api/routes.ts` (after the existing imports):

```typescript
import { planImplementation } from '../ai/implementationPlanner.js'
import { createImplementation } from '../db/implementations.js'
import { getSettings } from '../db/supabase.js'
```

Add this route at the end of the file, before the closing:

```typescript
router.post('/implement', async (req, res) => {
  try {
    const { ideaId, title, summary, bodyExcerpt } = req.body as {
      ideaId?: string
      title?: string
      summary?: string
      bodyExcerpt?: string
    }

    if (!ideaId || !title || !summary) {
      res.status(400).json({ error: 'ideaId, title, and summary are required' })
      return
    }

    const settings = await getSettings()
    const plan = await planImplementation({
      title,
      summary,
      bodyExcerpt: bodyExcerpt ?? '',
      model: settings.ollama_model,
      apiKey: settings.ollama_api_key,
      baseUrl: settings.scraper_base_url,
    })

    if (!plan) {
      res.status(500).json({ error: 'AI failed to generate implementation plan' })
      return
    }

    const implementation = await createImplementation({
      idea_id: ideaId,
      concept: plan.concept,
      description: plan.description,
      goals: plan.goals,
    })

    res.json(implementation)
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Unknown error'
    console.error('Implement failed:', message)
    res.status(500).json({ error: message })
  }
})
```

- [ ] **Step 2: Run existing scraper tests to confirm nothing broke**

```bash
cd painhunt-scraper && npm test
```

Expected: All existing tests PASS.

- [ ] **Step 3: Commit**

```bash
git add painhunt-scraper/src/api/routes.ts
git commit -m "feat(scraper): add POST /implement endpoint"
```

---

### Task 5: Android Domain Models

**Files:**
- Create: `painhunt-android/shared/src/commonMain/kotlin/com/painhunt/domain/ImplementationTask.kt`
- Create: `painhunt-android/shared/src/commonMain/kotlin/com/painhunt/domain/ImplementationGoal.kt`
- Create: `painhunt-android/shared/src/commonMain/kotlin/com/painhunt/domain/Implementation.kt`

- [ ] **Step 1: Create `ImplementationTask.kt`**

```kotlin
package com.painhunt.domain

import kotlinx.serialization.Serializable

@Serializable
data class ImplementationTask(
    val id: String,
    val task: String,
    val done: Boolean,
)
```

- [ ] **Step 2: Create `ImplementationGoal.kt`**

```kotlin
package com.painhunt.domain

import kotlinx.serialization.Serializable

@Serializable
data class ImplementationGoal(
    val goal: String,
    val tasks: List<ImplementationTask>,
)
```

- [ ] **Step 3: Create `Implementation.kt`**

```kotlin
package com.painhunt.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

- [ ] **Step 4: Commit**

```bash
git add painhunt-android/shared/src/commonMain/kotlin/com/painhunt/domain/ImplementationTask.kt \
        painhunt-android/shared/src/commonMain/kotlin/com/painhunt/domain/ImplementationGoal.kt \
        painhunt-android/shared/src/commonMain/kotlin/com/painhunt/domain/Implementation.kt
git commit -m "feat(android): add Implementation domain models"
```

---

### Task 6: ImplementationsRepository

**Files:**
- Create: `painhunt-android/shared/src/commonMain/kotlin/com/painhunt/data/ImplementationsRepository.kt`

- [ ] **Step 1: Create `ImplementationsRepository.kt`**

Follow the same pattern as `IdeasRepository.kt` and `SourcesRepository.kt`.

```kotlin
package com.painhunt.data

import com.painhunt.domain.Implementation
import com.painhunt.domain.ImplementationGoal
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable

class ImplementationsRepository(private val client: SupabaseClient) {

    @Serializable
    private data class GoalsUpdate(val goals: List<ImplementationGoal>)

    suspend fun getAll(): List<Implementation> =
        client.from("implementations").select {
            order("created_at", Order.DESCENDING)
        }.decodeList()

    suspend fun getById(id: String): Implementation =
        client.from("implementations").select {
            filter { eq("id", id) }
            limit(1)
        }.decodeSingle()

    suspend fun getByIdeaId(ideaId: String): Implementation? =
        client.from("implementations").select {
            filter { eq("idea_id", ideaId) }
            limit(1)
        }.decodeList<Implementation>().firstOrNull()

    suspend fun updateGoals(id: String, goals: List<ImplementationGoal>) {
        client.from("implementations").update(GoalsUpdate(goals)) {
            filter { eq("id", id) }
        }
    }

    suspend fun remove(id: String) {
        client.from("implementations").delete {
            filter { eq("id", id) }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add painhunt-android/shared/src/commonMain/kotlin/com/painhunt/data/ImplementationsRepository.kt
git commit -m "feat(android): add ImplementationsRepository"
```

---

### Task 7: ImplementingListViewModel

**Files:**
- Create: `painhunt-android/shared/src/commonMain/kotlin/com/painhunt/presentation/ImplementingListViewModel.kt`

- [ ] **Step 1: Create `ImplementingListViewModel.kt`**

Follow the pattern of `SourcesViewModel.kt`.

```kotlin
package com.painhunt.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.painhunt.data.ImplementationsRepository
import com.painhunt.domain.Implementation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImplementingListUiState(
    val implementations: List<Implementation> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class ImplementingListViewModel(
    private val implementationsRepository: ImplementationsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImplementingListUiState())
    val uiState: StateFlow<ImplementingListUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val implementations = implementationsRepository.getAll()
                _uiState.update { it.copy(implementations = implementations, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun remove(id: String) {
        viewModelScope.launch {
            try {
                implementationsRepository.remove(id)
                load()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add painhunt-android/shared/src/commonMain/kotlin/com/painhunt/presentation/ImplementingListViewModel.kt
git commit -m "feat(android): add ImplementingListViewModel"
```

---

### Task 8: ImplementingDetailViewModel

**Files:**
- Create: `painhunt-android/shared/src/commonMain/kotlin/com/painhunt/presentation/ImplementingDetailViewModel.kt`

- [ ] **Step 1: Create `ImplementingDetailViewModel.kt`**

```kotlin
package com.painhunt.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.painhunt.data.IdeasRepository
import com.painhunt.data.ImplementationsRepository
import com.painhunt.domain.Idea
import com.painhunt.domain.Implementation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImplementingDetailUiState(
    val implementation: Implementation? = null,
    val idea: Idea? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val removed: Boolean = false,
    val error: String? = null,
)

class ImplementingDetailViewModel(
    private val implementationsRepository: ImplementationsRepository,
    private val ideasRepository: IdeasRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImplementingDetailUiState())
    val uiState: StateFlow<ImplementingDetailUiState> = _uiState.asStateFlow()

    fun load(implementationId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val implementation = implementationsRepository.getById(implementationId)
                val idea = ideasRepository.getIdeaById(implementation.ideaId)
                _uiState.update { it.copy(implementation = implementation, idea = idea, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun toggleTask(goalIndex: Int, taskIndex: Int) {
        val impl = _uiState.value.implementation ?: return
        val updatedGoals = impl.goals.mapIndexed { gi, goal ->
            if (gi == goalIndex) {
                goal.copy(tasks = goal.tasks.mapIndexed { ti, task ->
                    if (ti == taskIndex) task.copy(done = !task.done) else task
                })
            } else goal
        }
        val updatedImpl = impl.copy(goals = updatedGoals)
        _uiState.update { it.copy(implementation = updatedImpl, isSaving = true) }
        viewModelScope.launch {
            try {
                implementationsRepository.updateGoals(impl.id, updatedGoals)
                _uiState.update { it.copy(isSaving = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(implementation = impl, isSaving = false, error = e.message) }
            }
        }
    }

    fun remove() {
        val impl = _uiState.value.implementation ?: return
        viewModelScope.launch {
            try {
                implementationsRepository.remove(impl.id)
                _uiState.update { it.copy(removed = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add painhunt-android/shared/src/commonMain/kotlin/com/painhunt/presentation/ImplementingDetailViewModel.kt
git commit -m "feat(android): add ImplementingDetailViewModel"
```

---

### Task 9: IdeaDetailViewModel — Implementing Support

**Files:**
- Modify: `painhunt-android/shared/src/commonMain/kotlin/com/painhunt/presentation/IdeaDetailViewModel.kt`

The current file is at `shared/src/commonMain/kotlin/com/painhunt/presentation/IdeaDetailViewModel.kt`. It has 64 lines. Read it before editing.

- [ ] **Step 1: Replace the entire file**

```kotlin
package com.painhunt.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.painhunt.data.BookmarksRepository
import com.painhunt.data.IdeasRepository
import com.painhunt.data.ImplementationsRepository
import com.painhunt.data.SettingsRepository
import com.painhunt.domain.Idea
import com.painhunt.domain.Note
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class IdeaDetailUiState(
    val idea: Idea? = null,
    val isBookmarked: Boolean = false,
    val note: Note? = null,
    val isLoading: Boolean = false,
    val isImplementing: Boolean = false,
    val implementationId: String? = null,
    val isStartingImplementation: Boolean = false,
    val error: String? = null,
)

class IdeaDetailViewModel(
    private val ideasRepository: IdeasRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val settingsRepository: SettingsRepository,
    private val implementationsRepository: ImplementationsRepository,
) : ViewModel() {

    @Serializable
    private data class ImplementRequest(
        val ideaId: String,
        val title: String,
        val summary: String,
        val bodyExcerpt: String,
    )

    private val httpClient = HttpClient()

    private val _uiState = MutableStateFlow(IdeaDetailUiState())
    val uiState: StateFlow<IdeaDetailUiState> = _uiState.asStateFlow()

    fun load(ideaId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val idea = ideasRepository.getIdeaById(ideaId)
                val bookmarkedIds = bookmarksRepository.getBookmarkedIdeaIds()
                val note = bookmarksRepository.getNoteForIdea(ideaId)
                val existing = implementationsRepository.getByIdeaId(ideaId)
                _uiState.update {
                    it.copy(
                        idea = idea,
                        isBookmarked = ideaId in bookmarkedIds,
                        note = note,
                        isLoading = false,
                        isImplementing = existing != null,
                        implementationId = existing?.id,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun toggleBookmark() {
        val ideaId = _uiState.value.idea?.id ?: return
        viewModelScope.launch {
            if (_uiState.value.isBookmarked) {
                bookmarksRepository.removeBookmark(ideaId)
                _uiState.update { it.copy(isBookmarked = false) }
            } else {
                bookmarksRepository.addBookmark(ideaId)
                _uiState.update { it.copy(isBookmarked = true) }
            }
        }
    }

    fun saveNote(content: String, tags: List<String>) {
        val ideaId = _uiState.value.idea?.id ?: return
        viewModelScope.launch {
            bookmarksRepository.upsertNote(ideaId, content, tags)
        }
    }

    fun startImplementing() {
        val idea = _uiState.value.idea ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isStartingImplementation = true, error = null) }
            try {
                val scraperBaseUrl = settingsRepository.get().scraperBaseUrl
                val requestBody = Json.encodeToString(
                    ImplementRequest(
                        ideaId = idea.id,
                        title = idea.title,
                        summary = idea.aiSummary,
                        bodyExcerpt = idea.bodyExcerpt ?: "",
                    )
                )
                val response = httpClient.post("$scraperBaseUrl/implement") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
                if (!response.status.isSuccess()) {
                    throw Exception("Server error ${response.status.value}: ${response.bodyAsText()}")
                }
                val existing = implementationsRepository.getByIdeaId(idea.id)
                _uiState.update {
                    it.copy(
                        isStartingImplementation = false,
                        isImplementing = existing != null,
                        implementationId = existing?.id,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isStartingImplementation = false, error = "Failed: ${e.message}") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add painhunt-android/shared/src/commonMain/kotlin/com/painhunt/presentation/IdeaDetailViewModel.kt
git commit -m "feat(android): add implementing support to IdeaDetailViewModel"
```

---

### Task 10: ImplementingListScreen

**Files:**
- Create: `painhunt-android/androidApp/src/main/kotlin/com/painhunt/ui/implementing/ImplementingListScreen.kt`

- [ ] **Step 1: Create `ImplementingListScreen.kt`**

```kotlin
package com.painhunt.ui.implementing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.painhunt.presentation.ImplementingListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImplementingListScreen(
    viewModel: ImplementingListViewModel,
    onImplementationClick: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Implementing") }) },
    ) { padding ->
        when {
            state.isLoading -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        Button(onClick = viewModel::load) { Text("Retry") }
                    }
                }
            }
            state.implementations.isEmpty() -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No ideas being implemented yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.padding(padding)) {
                    items(state.implementations, key = { it.id }) { impl ->
                        val totalTasks = impl.goals.sumOf { it.tasks.size }
                        val doneTasks = impl.goals.sumOf { g -> g.tasks.count { it.done } }
                        val percent = if (totalTasks > 0) doneTasks * 100 / totalTasks else 0

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clickable { onImplementationClick(impl.id) },
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(impl.concept, style = MaterialTheme.typography.titleMedium)
                                LinearProgressIndicator(
                                    progress = { percent / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    "$percent% complete",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add painhunt-android/androidApp/src/main/kotlin/com/painhunt/ui/implementing/ImplementingListScreen.kt
git commit -m "feat(android): add ImplementingListScreen"
```

---

### Task 11: ImplementingDetailScreen

**Files:**
- Create: `painhunt-android/androidApp/src/main/kotlin/com/painhunt/ui/implementing/ImplementingDetailScreen.kt`

- [ ] **Step 1: Create `ImplementingDetailScreen.kt`**

```kotlin
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
                title = { Text(state.implementation?.concept ?: "") },
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
```

- [ ] **Step 2: Commit**

```bash
git add painhunt-android/androidApp/src/main/kotlin/com/painhunt/ui/implementing/ImplementingDetailScreen.kt
git commit -m "feat(android): add ImplementingDetailScreen"
```

---

### Task 12: IdeaDetailScreen — Implement Button

**Files:**
- Modify: `painhunt-android/androidApp/src/main/kotlin/com/painhunt/ui/detail/IdeaDetailScreen.kt`

The current file ends at line 133. Read it before editing.

- [ ] **Step 1: Add `onNavigateToImplementation` param**

Replace the function signature:
```kotlin
fun IdeaDetailScreen(
    ideaId: String,
    viewModel: IdeaDetailViewModel,
    onBack: () -> Unit,
) {
```
With:
```kotlin
fun IdeaDetailScreen(
    ideaId: String,
    viewModel: IdeaDetailViewModel,
    onBack: () -> Unit,
    onNavigateToImplementation: (String) -> Unit,
) {
```

- [ ] **Step 2: Add implement button after Save Notes button**

In the `Column` body, after the `Button { Text("Save Notes") }` block and before `if (state.error != null)`, add:

```kotlin
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
```

- [ ] **Step 3: Add missing imports**

`FilledTonalButton` is from `androidx.compose.material3.*` (already imported via wildcard). `Spacer` and `width` are from `androidx.compose.foundation.layout.*` (already imported). No new imports needed.

- [ ] **Step 4: Commit**

```bash
git add painhunt-android/androidApp/src/main/kotlin/com/painhunt/ui/detail/IdeaDetailScreen.kt
git commit -m "feat(android): add implement button to IdeaDetailScreen"
```

---

### Task 13: MainActivity — Wiring

**Files:**
- Modify: `painhunt-android/androidApp/src/main/kotlin/com/painhunt/app/MainActivity.kt`

Read the current file before editing — it is ~104 lines.

- [ ] **Step 1: Add new imports**

After the existing imports block, add:
```kotlin
import com.painhunt.data.ImplementationsRepository
import com.painhunt.presentation.ImplementingDetailViewModel
import com.painhunt.presentation.ImplementingListViewModel
import com.painhunt.ui.implementing.ImplementingDetailScreen
import com.painhunt.ui.implementing.ImplementingListScreen
import androidx.compose.material.icons.filled.Build
```

- [ ] **Step 2: Add new routes**

After the existing route declarations (`@Serializable object FeedRoute` etc.), add:
```kotlin
@Serializable object ImplementingRoute
@Serializable data class ImplementingDetailRoute(val implementationId: String)
```

- [ ] **Step 3: Instantiate the new repository**

After `val settingsRepo = SettingsRepository(supabase)`, add:
```kotlin
val implementationsRepo = ImplementationsRepository(supabase)
```

- [ ] **Step 4: Add Implementing bottom bar item**

Inside `NavigationBar { ... }`, after the existing Feed `NavigationBarItem`, add:
```kotlin
            NavigationBarItem(
                selected = backStack?.destination?.hasRoute(ImplementingRoute::class) == true,
                onClick = { navController.navigate(ImplementingRoute) { launchSingleTop = true } },
                icon = { Icon(Icons.Default.Build, null) },
                label = { Text("Implementing") },
            )
```

- [ ] **Step 5: Update IdeaDetailScreen composable call**

Replace:
```kotlin
                        composable<DetailRoute> { entry ->
                            val route = entry.toRoute<DetailRoute>()
                            val vm = viewModel { IdeaDetailViewModel(ideasRepo, bookmarksRepo) }
                            IdeaDetailScreen(route.ideaId, vm) { navController.popBackStack() }
                        }
```
With:
```kotlin
                        composable<DetailRoute> { entry ->
                            val route = entry.toRoute<DetailRoute>()
                            val vm = viewModel { IdeaDetailViewModel(ideasRepo, bookmarksRepo, settingsRepo, implementationsRepo) }
                            IdeaDetailScreen(
                                ideaId = route.ideaId,
                                viewModel = vm,
                                onBack = { navController.popBackStack() },
                                onNavigateToImplementation = { implId -> navController.navigate(ImplementingDetailRoute(implId)) },
                            )
                        }
```

- [ ] **Step 6: Add Implementing routes to NavHost**

After the existing `composable<SettingsRoute>` block, add:
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

- [ ] **Step 7: Run full scraper test suite to confirm backend is clean**

```bash
cd painhunt-scraper && npm test
```

Expected: All tests PASS.

- [ ] **Step 8: Commit**

```bash
git add painhunt-android/androidApp/src/main/kotlin/com/painhunt/app/MainActivity.kt
git commit -m "feat(android): wire Implementing feature into navigation and bottom bar"
```
