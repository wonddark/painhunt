// src/api/routes.ts
import { Router } from 'express'
import { runScrape, runScrapeFromPosts } from '../pipeline.js'
import { parseRedditJson } from '../reddit/client.js'
import { getSettings } from '../db/supabase.js'
import { planImplementation } from '../ai/implementationPlanner.js'
import { createImplementation } from '../db/implementations.js'
import { streamIdeaChat } from '../ai/ideaChat.js'
import type { ChatMessageInput } from '../ai/ideaChat.js'

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
    if (posts.length === 0) {
      res.json({ inserted: 0, discarded: 0 })
      return
    }
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
      baseUrl: process.env.OLLAMA_BASE_URL ?? 'https://api.ollama.com',
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

router.post('/chat', async (req, res) => {
  try {
    const { ideaId, messages } = req.body as {
      ideaId?: string
      messages?: ChatMessageInput[]
    }

    if (!ideaId || !Array.isArray(messages)) {
      res.status(400).json({ error: 'ideaId and messages array are required' })
      return
    }

    await streamIdeaChat(ideaId, messages, res)
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Unknown error'
    console.error('Chat failed:', message)
    if (!res.headersSent) res.status(500).json({ error: message })
  }
})
