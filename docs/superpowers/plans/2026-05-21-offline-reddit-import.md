# Offline Reddit Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users download Reddit JSON from a browser, upload it via the Android app, and have the scraper process it through the same pipeline as a live scrape.

**Architecture:** A new `POST /scrape/upload` route accepts the raw Reddit JSON body, parses it via `parseRedditJson()` (new function in `reddit/client.ts`), auto-upserts the subreddit into the DB, then delegates to `processPosts()` (extracted from `runScrape`). The Android Feed screen gains a secondary "Import File" button backed by `FeedViewModel.uploadFile()`.

**Tech Stack:** Node/Express/TypeScript (scraper), Vitest (scraper tests), Kotlin/Jetpack Compose/Ktor (Android)

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Modify | `painhunt-scraper/src/db/supabase.ts` | Add `upsertSubredditByName` |
| Modify | `painhunt-scraper/src/reddit/client.ts` | Add `parseRedditJson` + `ParsedRedditData` type |
| Modify | `painhunt-scraper/src/pipeline.ts` | Extract `processPosts`, add `runScrapeFromPosts` |
| Modify | `painhunt-scraper/src/api/routes.ts` | Add `POST /scrape/upload` |
| Modify | `painhunt-scraper/src/index.ts` | Raise body size limit to `5mb` |
| Modify | `painhunt-scraper/tests/db/supabase.test.ts` | Test `upsertSubredditByName` |
| Modify | `painhunt-scraper/tests/reddit/client.test.ts` | Test `parseRedditJson` |
| Modify | `painhunt-android/shared/src/commonMain/kotlin/com/painhunt/presentation/FeedViewModel.kt` | Add `isUploading` state + `uploadFile` |
| Modify | `painhunt-android/androidApp/src/main/kotlin/com/painhunt/ui/feed/FeedScreen.kt` | File picker + "Import File" button |

---

### Task 1: Add `upsertSubredditByName` to `db/supabase.ts`

**Files:**
- Modify: `painhunt-scraper/src/db/supabase.ts`
- Modify: `painhunt-scraper/tests/db/supabase.test.ts`

- [ ] **Step 1: Write the failing tests**

Append to `painhunt-scraper/tests/db/supabase.test.ts`:

```ts
import { getActiveSubreddits, getSettings, upsertIdea, upsertSubredditByName } from '../../src/db/supabase.js'

describe('upsertSubredditByName', () => {
  beforeEach(() => {
    mockFromFn.mockClear()
  })

  it('returns existing subreddit when found', async () => {
    const existing = { id: 'sub1', name: 'entrepreneur', active: true, added_at: '2026-01-01' }
    mockFromFn.mockReturnValueOnce({
      select: vi.fn().mockReturnThis(),
      eq: vi.fn().mockReturnThis(),
      maybeSingle: vi.fn().mockResolvedValueOnce({ data: existing, error: null }),
    })

    const result = await upsertSubredditByName('entrepreneur')
    expect(result).toEqual(existing)
  })

  it('inserts and returns new subreddit when not found', async () => {
    const inserted = { id: 'sub2', name: 'startups', active: true, added_at: '2026-01-01' }
    mockFromFn
      .mockReturnValueOnce({
        select: vi.fn().mockReturnThis(),
        eq: vi.fn().mockReturnThis(),
        maybeSingle: vi.fn().mockResolvedValueOnce({ data: null, error: null }),
      })
      .mockReturnValueOnce({
        insert: vi.fn().mockReturnValue({
          select: vi.fn().mockReturnValue({
            single: vi.fn().mockResolvedValueOnce({ data: inserted, error: null }),
          }),
        }),
      })

    const result = await upsertSubredditByName('startups')
    expect(result).toEqual(inserted)
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd painhunt-scraper && npm test -- tests/db/supabase.test.ts
```

Expected: FAIL — `upsertSubredditByName` is not exported.

- [ ] **Step 3: Implement `upsertSubredditByName` in `db/supabase.ts`**

Append to the bottom of `painhunt-scraper/src/db/supabase.ts`:

```ts
export async function upsertSubredditByName(name: string): Promise<Subreddit> {
  const { data: existing } = await client
    .from('subreddits')
    .select()
    .eq('name', name)
    .maybeSingle()

  if (existing) return existing as Subreddit

  const { data, error } = await client
    .from('subreddits')
    .insert({ name, active: true })
    .select()
    .single()
  if (error) throwDbError(error)
  return data as Subreddit
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd painhunt-scraper && npm test -- tests/db/supabase.test.ts
```

Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add painhunt-scraper/src/db/supabase.ts painhunt-scraper/tests/db/supabase.test.ts
git commit -m "feat(scraper): add upsertSubredditByName to supabase client"
```

---

### Task 2: Add `parseRedditJson` to `reddit/client.ts`

**Files:**
- Modify: `painhunt-scraper/src/reddit/client.ts`
- Modify: `painhunt-scraper/tests/reddit/client.test.ts`

- [ ] **Step 1: Write the failing tests**

Append to `painhunt-scraper/tests/reddit/client.test.ts`:

```ts
import { fetchSubredditPosts, parseRedditJson } from '../../src/reddit/client.js'

describe('parseRedditJson', () => {
  const makeChild = (id: string, subreddit = 'entrepreneur') => ({
    data: {
      id,
      title: `Post ${id}`,
      selftext: 'body text',
      author: 'alice',
      score: 42,
      permalink: `/r/${subreddit}/comments/${id}/post_${id}/`,
      subreddit,
    },
  })

  it('maps Reddit JSON children to RedditPost array', () => {
    const json = { data: { children: [makeChild('abc'), makeChild('def')] } }
    const { posts, subredditName } = parseRedditJson(json)

    expect(posts).toHaveLength(2)
    expect(posts[0]).toEqual({
      id: 'abc',
      title: 'Post abc',
      selftext: 'body text',
      author: 'alice',
      score: 42,
      permalink: '/r/entrepreneur/comments/abc/post_abc/',
    })
    expect(subredditName).toBe('entrepreneur')
  })

  it('deduplicates posts by id', () => {
    const json = { data: { children: [makeChild('dup'), makeChild('dup'), makeChild('uniq')] } }
    const { posts } = parseRedditJson(json)
    expect(posts).toHaveLength(2)
  })

  it('returns empty posts and empty subredditName when data.children is missing', () => {
    const { posts, subredditName } = parseRedditJson({ kind: 'Listing' })
    expect(posts).toHaveLength(0)
    expect(subredditName).toBe('')
  })

  it('returns empty posts when children is an empty array', () => {
    const { posts, subredditName } = parseRedditJson({ data: { children: [] } })
    expect(posts).toHaveLength(0)
    expect(subredditName).toBe('')
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd painhunt-scraper && npm test -- tests/reddit/client.test.ts
```

Expected: FAIL — `parseRedditJson` is not exported.

- [ ] **Step 3: Implement `parseRedditJson` in `reddit/client.ts`**

Add the `ParsedRedditData` type and function. Append to `painhunt-scraper/src/reddit/client.ts`:

```ts
export type ParsedRedditData = {
  posts: RedditPost[]
  subredditName: string
}

export function parseRedditJson(json: unknown): ParsedRedditData {
  const children = (json as any)?.data?.children
  if (!Array.isArray(children) || children.length === 0) {
    return { posts: [], subredditName: '' }
  }

  const seen = new Set<string>()
  const posts: RedditPost[] = []
  let subredditName = ''

  for (const child of children) {
    const d = child?.data
    if (!d || typeof d.id !== 'string') continue
    if (seen.has(d.id)) continue
    seen.add(d.id)
    if (!subredditName && typeof d.subreddit === 'string') {
      subredditName = d.subreddit
    }
    posts.push({
      id: d.id,
      title: typeof d.title === 'string' ? d.title : String(d.title ?? ''),
      selftext: typeof d.selftext === 'string' ? d.selftext : '',
      author: typeof d.author === 'string' ? d.author : '',
      score: typeof d.score === 'number' ? d.score : 0,
      permalink: typeof d.permalink === 'string' ? d.permalink : '',
    })
  }

  return { posts, subredditName }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd painhunt-scraper && npm test -- tests/reddit/client.test.ts
```

Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add painhunt-scraper/src/reddit/client.ts painhunt-scraper/tests/reddit/client.test.ts
git commit -m "feat(scraper): add parseRedditJson for offline Reddit JSON import"
```

---

### Task 3: Refactor `pipeline.ts` — extract `processPosts`, add `runScrapeFromPosts`

**Files:**
- Modify: `painhunt-scraper/src/pipeline.ts`
- Verify: `painhunt-scraper/tests/pipeline.test.ts` (no changes needed — must still pass)

- [ ] **Step 1: Rewrite `pipeline.ts`**

Replace the entire content of `painhunt-scraper/src/pipeline.ts` with:

```ts
import { fetchSubredditPosts } from './reddit/client.js'
import { matchesPainSignal } from './filter/keywords.js'
import { scorePost } from './ai/ollama.js'
import { getSettings, getActiveSubreddits, upsertIdea, upsertSubredditByName } from './db/supabase.js'
import type { RedditPost } from './reddit/client.js'
import type { Subreddit, Settings } from './db/supabase.js'

export type ScrapeResult = {
  inserted: number
  discarded: number
}

async function processPosts(
  posts: RedditPost[],
  subreddit: Subreddit,
  settings: Settings,
): Promise<ScrapeResult> {
  let inserted = 0
  let discarded = 0

  for (const post of posts) {
    if (post.score > 0 && post.score < settings.min_upvotes_threshold) {
      discarded++
      continue
    }

    if (!matchesPainSignal(post.title, post.selftext)) {
      discarded++
      continue
    }

    const aiResult = await scorePost({
      title: post.title,
      body: post.selftext,
      model: settings.ollama_model,
      apiKey: settings.ollama_api_key,
      baseUrl: process.env.OLLAMA_BASE_URL ?? 'https://api.ollama.com',
    })

    if (!aiResult || aiResult.relevanceScore < 40) {
      discarded++
      continue
    }

    await upsertIdea({
      reddit_post_id: post.id,
      subreddit_id: subreddit.id,
      title: post.title,
      body_excerpt: post.selftext.slice(0, 500),
      url: `https://reddit.com${post.permalink}`,
      author: post.author,
      reddit_score: post.score,
      ai_relevance_score: aiResult.relevanceScore,
      ai_summary: aiResult.summary,
      ai_category: aiResult.category,
    })
    inserted++
  }

  return { inserted, discarded }
}

export async function runScrape(): Promise<ScrapeResult> {
  const [settings, subreddits] = await Promise.all([getSettings(), getActiveSubreddits()])

  if (subreddits.length === 0) {
    return { inserted: 0, discarded: 0 }
  }

  let inserted = 0
  let discarded = 0

  for (const subreddit of subreddits) {
    const posts = await fetchSubredditPosts(subreddit.name)
    const result = await processPosts(posts, subreddit, settings)
    inserted += result.inserted
    discarded += result.discarded
  }

  return { inserted, discarded }
}

export async function runScrapeFromPosts(posts: RedditPost[], subredditName: string): Promise<ScrapeResult> {
  if (posts.length === 0) return { inserted: 0, discarded: 0 }
  const [subreddit, settings] = await Promise.all([
    upsertSubredditByName(subredditName),
    getSettings(),
  ])
  return processPosts(posts, subreddit, settings)
}
```

- [ ] **Step 2: Run existing pipeline tests to verify no regression**

```bash
cd painhunt-scraper && npm test -- tests/pipeline.test.ts
```

Expected: PASS — `runScrape` behaviour unchanged.

- [ ] **Step 3: Run full test suite**

```bash
cd painhunt-scraper && npm test
```

Expected: all PASS.

- [ ] **Step 4: Commit**

```bash
git add painhunt-scraper/src/pipeline.ts
git commit -m "refactor(scraper): extract processPosts, add runScrapeFromPosts"
```

---

### Task 4: Add `POST /scrape/upload` route + raise body size limit

**Files:**
- Modify: `painhunt-scraper/src/api/routes.ts`
- Modify: `painhunt-scraper/src/index.ts`

- [ ] **Step 1: Update `routes.ts`**

Replace the entire content of `painhunt-scraper/src/api/routes.ts` with:

```ts
// src/api/routes.ts
import { Router } from 'express'
import { runScrape, runScrapeFromPosts } from '../pipeline.js'
import { parseRedditJson } from '../reddit/client.js'

export const router = Router()

router.get('/health', (_req, res) => {
  res.json({ status: 'ok' })
})

router.post('/scrape', async (_req, res) => {
  try {
    const result = await runScrape()
    res.json(result)
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Unknown error'
    console.error('Scrape failed:', message)
    res.status(500).json({ error: message })
  }
})

router.post('/scrape/upload', async (req, res) => {
  try {
    const { posts, subredditName } = parseRedditJson(req.body)
    if (!subredditName) {
      res.status(400).json({ error: 'Invalid Reddit JSON format' })
      return
    }
    const result = await runScrapeFromPosts(posts, subredditName)
    res.json(result)
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Unknown error'
    console.error('Upload scrape failed:', message)
    res.status(500).json({ error: message })
  }
})
```

- [ ] **Step 2: Raise body size limit in `index.ts`**

Replace the entire content of `painhunt-scraper/src/index.ts` with:

```ts
// src/index.ts
import express from 'express'
import { router } from './api/routes.js'

const app = express()
app.use(express.json({ limit: '5mb' }))
app.use(router)

const port = parseInt(process.env.PORT ?? '3000', 10)
app.listen(port, () => {
  console.log(`painhunt-scraper running on port ${port}`)
})
```

- [ ] **Step 3: Run full test suite**

```bash
cd painhunt-scraper && npm test
```

Expected: all PASS.

- [ ] **Step 4: Commit**

```bash
git add painhunt-scraper/src/api/routes.ts painhunt-scraper/src/index.ts
git commit -m "feat(scraper): add POST /scrape/upload endpoint for offline JSON import"
```

---

### Task 5: Android — add `isUploading` state + `uploadFile` to `FeedViewModel`

**Files:**
- Modify: `painhunt-android/shared/src/commonMain/kotlin/com/painhunt/presentation/FeedViewModel.kt`

- [ ] **Step 1: Update `FeedViewModel.kt`**

Replace the entire content of the file with:

```kotlin
package com.painhunt.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.painhunt.data.IdeasRepository
import com.painhunt.data.SettingsRepository
import com.painhunt.data.SortField
import com.painhunt.domain.Idea
import io.ktor.client.HttpClient
import io.ktor.client.request.contentType
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class FeedUiState(
    val ideas: List<Idea> = emptyList(),
    val isLoading: Boolean = false,
    val isScraping: Boolean = false,
    val isUploading: Boolean = false,
    val sortBy: SortField = SortField.ScrapedAt,
    val selectedCategory: String? = null,
    val error: String? = null,
    val scrapeResult: String? = null,
)

class FeedViewModel(
    private val ideasRepository: IdeasRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val httpClient = HttpClient()

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    init {
        loadIdeas()
    }

    fun loadIdeas() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val ideas = ideasRepository.getIdeas(
                    sortBy = _uiState.value.sortBy,
                    category = _uiState.value.selectedCategory,
                )
                _uiState.update { it.copy(ideas = ideas, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setSortBy(sortBy: SortField) {
        _uiState.update { it.copy(sortBy = sortBy) }
        loadIdeas()
    }

    fun setCategory(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
        loadIdeas()
    }

    fun triggerScrape() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScraping = true, scrapeResult = null) }
            try {
                val scraperBaseUrl = settingsRepository.get().scraperBaseUrl
                val response = httpClient.post("$scraperBaseUrl/scrape")
                val body = response.bodyAsText()
                _uiState.update { it.copy(isScraping = false, scrapeResult = body) }
                loadIdeas()
            } catch (e: Exception) {
                _uiState.update { it.copy(isScraping = false, error = "Scraper offline: ${e.message}") }
            }
        }
    }

    fun uploadFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, scrapeResult = null) }
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                    ?: throw IOException("Cannot read file")
                val scraperBaseUrl = settingsRepository.get().scraperBaseUrl
                val response = httpClient.post("$scraperBaseUrl/scrape/upload") {
                    contentType(ContentType.Application.Json)
                    setBody(bytes)
                }
                val body = response.bodyAsText()
                _uiState.update { it.copy(isUploading = false, scrapeResult = body) }
                loadIdeas()
            } catch (e: Exception) {
                _uiState.update { it.copy(isUploading = false, error = "Upload failed: ${e.message}") }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }
}
```

- [ ] **Step 2: Verify the project builds**

```bash
cd painhunt-android && ./gradlew :shared:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add painhunt-android/shared/src/commonMain/kotlin/com/painhunt/presentation/FeedViewModel.kt
git commit -m "feat(android): add uploadFile and isUploading state to FeedViewModel"
```

---

### Task 6: Android — add file picker + "Import File" button to `FeedScreen`

**Files:**
- Modify: `painhunt-android/androidApp/src/main/kotlin/com/painhunt/ui/feed/FeedScreen.kt`

- [ ] **Step 1: Update `FeedScreen.kt`**

Replace the entire content of the file with:

```kotlin
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
fun FeedScreen(viewModel: FeedViewModel, onIdeaClick: (String) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
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
                        Button(onClick = viewModel::triggerScrape, enabled = !state.isScraping) { Text("Trigger Scrape") }
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
```

- [ ] **Step 2: Verify the project builds**

```bash
cd painhunt-android && ./gradlew :androidApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the full scraper test suite one final time**

```bash
cd painhunt-scraper && npm test
```

Expected: all PASS.

- [ ] **Step 4: Commit**

```bash
git add painhunt-android/androidApp/src/main/kotlin/com/painhunt/ui/feed/FeedScreen.kt
git commit -m "feat(android): add Import File button and file picker to FeedScreen"
```
