# Idea Detail — AI Chat Tab

**Date:** 2026-05-25
**Status:** Approved

## Overview

Split `IdeaDetailScreen` into two tabs. Tab 0 ("Overview") keeps all existing content unchanged. Tab 1 ("Discuss") is a persistent multi-turn chat with the AI, scoped to the idea and its original Reddit post.

## Tabs UX

`IdeaDetailScreen` gains a `TabRow` with two tabs rendered inside the `Scaffold` content area. No `HorizontalPager` — a single `selectedTabIndex` state drives an `if/else` between two composables.

```
Scaffold
  TopAppBar (unchanged — bookmark + open-in-browser)
  Column
    TabRow
      Tab("Overview")   ← existing scrollable content, verbatim
      Tab("Discuss")    ← new chat UI
    if selectedTab == 0 → OverviewContent()
    if selectedTab == 1 → ChatContent()
```

`OverviewContent` is the existing column body extracted into a private composable. Zero behavior change.

## Chat UI

`ChatContent` fills remaining screen height below `TabRow`.

```
Box(fillMaxSize)
  LazyColumn(reverseLayout = true)
    items(messages.reversed())
      UserBubble      → right-aligned, filled container color
      AssistantBubble → left-aligned, surface variant color
    if isStreaming → AssistantBubble(streamingText, cursor = true)
  Row(align = bottom)
    OutlinedTextField(modifier = weight(1f))
    IconButton(Send, enabled = !isStreaming)
```

- **Loading:** `CircularProgressIndicator` centered while history loads
- **Empty state:** centered hint text — "Ask anything about this idea."
- **Streaming bubble:** appears immediately; text grows token by token; `|` cursor appended; Send disabled during stream

## Data Model

### Supabase migration `003_chat_messages.sql`

```sql
CREATE TABLE chat_messages (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  idea_id    uuid NOT NULL REFERENCES ideas(id) ON DELETE CASCADE,
  role       text NOT NULL CHECK (role IN ('user', 'assistant')),
  content    text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX chat_messages_idea_id_idx ON chat_messages(idea_id, created_at);
```

### Android domain model

`shared/commonMain/kotlin/com/painhunt/domain/ChatMessage.kt`:

```kotlin
@Serializable
data class ChatMessage(
    val id: String,
    @SerialName("idea_id") val ideaId: String,
    val role: String,   // "user" | "assistant"
    val content: String,
    @SerialName("created_at") val createdAt: String,
)
```

### ChatRepository

`shared/commonMain/kotlin/com/painhunt/data/ChatRepository.kt` — follows existing `BookmarksRepository` pattern:

- `getMessagesForIdea(ideaId: String): List<ChatMessage>`
- `insertMessage(ideaId: String, role: String, content: String): ChatMessage`

## Scraper Backend

New endpoint `POST /chat`. New file `src/ai/ideaChat.ts`.

**Request:**
```json
{
  "ideaId": "uuid",
  "messages": [{ "role": "user|assistant", "content": "..." }]
}
```

**Handler flow:**
1. Fetch idea by `ideaId` from Supabase (title, summary, bodyExcerpt)
2. Fetch settings (model, apiKey, baseUrl)
3. Build system message injected at position 0 of the messages array:
   ```
   You are an expert advisor discussing a business idea found on Reddit.
   Title: "{title}"
   Summary: "{summary}"
   Original post excerpt: "{bodyExcerpt}"
   Help the user think deeply about this idea.
   ```
4. Call Ollama `/api/chat` with `stream: true`
5. Set response header `Content-Type: text/event-stream`, pipe each `data:` chunk through
6. Send `data: [DONE]` on stream end

## Android Architecture

### IdeaChatViewModel

`shared/commonMain/kotlin/com/painhunt/presentation/IdeaChatViewModel.kt` — separate from `IdeaDetailViewModel`.

```kotlin
data class IdeaChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val streamingText: String = "",
    val isStreaming: Boolean = false,
    val isLoadingHistory: Boolean = false,
    val error: String? = null,
)
```

**`load(ideaId)`** — fetches history from `ChatRepository`.

**`send(ideaId, text)`:**
1. Insert user message via `ChatRepository`, append to `messages`
2. Set `isStreaming = true`, `streamingText = ""`
3. POST to `$scraperBaseUrl/chat` with full message history
4. Consume SSE stream via Ktor `bodyAsChannel()`, read line-by-line
5. Each line: strip `data: ` prefix; if value is `[DONE]` stop; otherwise parse as JSON `{"message":{"content":"<token>"}}` and append token to `streamingText`
6. On stream end: insert completed assistant message to Supabase, move `streamingText` into `messages`, clear `streamingText`, set `isStreaming = false`

### Wiring

`IdeaChatViewModel(chatRepository, settingsRepository)` instantiated in `MainActivity` alongside existing VMs. Passed into `IdeaDetailScreen`, forwarded to `ChatContent`.

## Files Changed

| File | Change |
|------|--------|
| `supabase/migrations/003_chat_messages.sql` | new |
| `shared/.../domain/ChatMessage.kt` | new |
| `shared/.../data/ChatRepository.kt` | new |
| `shared/.../presentation/IdeaChatViewModel.kt` | new |
| `painhunt-scraper/src/ai/ideaChat.ts` | new |
| `painhunt-scraper/src/api/routes.ts` | add `/chat` route |
| `androidApp/.../ui/detail/IdeaDetailScreen.kt` | add tabs + ChatContent composable |
| `androidApp/.../app/MainActivity.kt` | instantiate IdeaChatViewModel |
