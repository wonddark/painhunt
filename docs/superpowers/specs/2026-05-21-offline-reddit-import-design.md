# Offline Reddit Import

**Date:** 2026-05-21
**Status:** Approved

## Problem

Reddit blocks automated scrape requests (403). The live `POST /scrape` endpoint is unreliable. Users need an offline fallback: download Reddit JSON manually from a browser, then import it into the app.

## Solution

Add a `POST /scrape/upload` endpoint to the scraper that accepts a raw Reddit JSON body and runs it through the same processing pipeline. The Android app gains a secondary "Import File" button on the Feed screen to pick and upload a local JSON file.

## Data Flow

```
User downloads reddit.com/r/<sub>/new.json in browser → saves file
→ Android: "Import File" button → file picker (application/json)
→ FeedViewModel.uploadFile(context, uri)
  → ContentResolver reads file bytes
  → POST <scraperUrl>/scrape/upload  Content-Type: application/json (raw Reddit JSON)
→ Scraper: parseRedditJson() → RedditPost[]
  → read subreddit name from posts[0].subreddit
  → lookup subreddit in DB; if absent, insert as active
  → processPosts(posts, subreddit) — same keyword filter + AI score + upsert
  → { inserted, discarded }
→ Android: update scrapeResult, reload ideas
```

## Scraper Changes

### `reddit/client.ts` — `parseRedditJson(json: unknown): RedditPost[]`

Parses Reddit JSON API format:

```
json.data.children[].data → {
  id, title, selftext, author, score, permalink, subreddit
}
```

Maps to existing `RedditPost` type. Deduplicates by `id`. Returns empty array if structure is missing/malformed (no throw — caller handles empty gracefully).

### `pipeline.ts` — extract `processPosts`

Extract shared function:

```ts
async function processPosts(
  posts: RedditPost[],
  subreddit: { id: string; name: string },
  settings: Settings,
): Promise<ScrapeResult>
```

`runScrape()` calls it per subreddit (no behaviour change). Upload endpoint calls it once with the parsed posts and looked-up subreddit.

### `api/routes.ts` — `POST /scrape/upload`

1. Receive raw Reddit JSON body (already parsed by `express.json()`).
2. Call `parseRedditJson(req.body)` → `RedditPost[]`.
3. If empty → return `{ inserted: 0, discarded: 0 }`.
4. Read subreddit name from `posts[0].subreddit`.
5. Look up subreddit in DB by name; if absent, insert as active.
6. Load settings from DB.
7. Call `processPosts(posts, subreddit, settings)`.
8. Return `ScrapeResult`.

Error responses:
- Body missing `data.children` → 400 `{ error: "Invalid Reddit JSON format" }`
- DB / AI failure → 500 (same as existing `/scrape`)

### `src/index.ts`

Raise body size limit:

```ts
app.use(express.json({ limit: '5mb' }))
```

## Android Changes

### `FeedUiState`

Add field: `isUploading: Boolean = false`

### `FeedViewModel.kt` — `uploadFile(context: Context, uri: Uri)`

```
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
```

### `FeedScreen.kt`

- Add `rememberLauncherForActivityResult(ActivityResultContracts.GetContent())` with MIME `"application/json"`.
- File picker cancelled (null URI) → no-op.
- Add `OutlinedButton` labelled "Import File" below existing primary "Scrape" button.
- Disabled and shows `CircularProgressIndicator` when `isUploading`.
- Existing "Scrape" button also disabled while `isUploading`.

## Testing

### New: `tests/reddit/client.test.ts` — `parseRedditJson`

- Valid Reddit JSON fixture → correct `RedditPost[]` mapping
- Duplicate `id` entries → deduplicated
- Missing `data.children` → returns `[]`
- Empty `children` array → returns `[]`

### Existing tests

`pipeline.test.ts` untouched — `processPosts` extraction preserves existing interface for `runScrape`.

## Invariants

- V1: Upload path runs identical keyword + AI filter as live scrape — no bypass.
- V2: Subreddit auto-insert on upload → always active.
- V3: Body size limit ≥ 5mb before upload endpoint is reachable.
