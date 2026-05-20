import { fetchSubredditPosts } from './reddit/client.js'
import { matchesPainSignal } from './filter/keywords.js'
import { scorePost } from './ai/ollama.js'
import { getSettings, getActiveSubreddits, upsertIdea } from './db/supabase.js'

export type ScrapeResult = {
  inserted: number
  discarded: number
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

    for (const post of posts) {
      if (post.score < settings.min_upvotes_threshold) {
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
  }

  return { inserted, discarded }
}
