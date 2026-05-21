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
