// src/api/routes.ts
import { Router } from 'express'
import { runScrape } from '../pipeline.js'

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
