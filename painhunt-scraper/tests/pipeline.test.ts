import { describe, it, expect, vi } from 'vitest'
import { runScrape } from '../src/pipeline.js'

vi.mock('../src/reddit/auth.js', () => ({ getRedditToken: vi.fn().mockResolvedValue('tok') }))
vi.mock('../src/reddit/client.js', () => ({
  fetchSubredditPosts: vi.fn().mockResolvedValue([
    { id: 'p1', title: 'I wish there was an app for this', selftext: '', score: 15, author: 'u1', permalink: '/r/test/p1' },
    { id: 'p2', title: 'Cool project launch', selftext: '', score: 100, author: 'u2', permalink: '/r/test/p2' },
    { id: 'p3', title: 'frustrated with manual invoicing', selftext: '', score: 3, author: 'u3', permalink: '/r/test/p3' },
  ]),
}))
vi.mock('../src/filter/keywords.js', async () => {
  const actual = await vi.importActual<typeof import('../src/filter/keywords.js')>('../src/filter/keywords.js')
  return actual
})
vi.mock('../src/ai/ollama.js', () => ({
  scorePost: vi.fn().mockResolvedValue({ relevanceScore: 75, summary: 'Pain point summary.', category: 'SaaS' }),
}))
vi.mock('../src/db/supabase.js', () => ({
  getSettings: vi.fn().mockResolvedValue({
    id: 's1',
    ollama_api_key: 'key',
    ollama_model: 'llama3.2',
    min_upvotes_threshold: 10,
    scraper_base_url: 'http://localhost:3000',
  }),
  getActiveSubreddits: vi.fn().mockResolvedValue([{ id: 'sub1', name: 'SomebodyMakeThis', active: true, added_at: '2026-01-01' }]),
  upsertIdea: vi.fn().mockResolvedValue(undefined),
}))

describe('runScrape', () => {
  it('skips posts below upvote threshold or without pain signal, inserts the rest', async () => {
    const result = await runScrape()

    // p1: score 15 >= 10 ✓, has pain signal "wish there was" ✓ → inserted
    // p2: score 100 >= 10 ✓, but no pain signal → discarded
    // p3: score 3 < 10 → discarded (upvote threshold)
    expect(result.inserted).toBe(1)
    expect(result.discarded).toBe(2)
  })
})
