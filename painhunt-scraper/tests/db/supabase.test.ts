import { describe, it, expect, vi, beforeEach } from 'vitest'

// Setup env vars before mocking
process.env.SUPABASE_URL = 'https://test.supabase.co'
process.env.SUPABASE_SERVICE_ROLE_KEY = 'test-key'

// Use vi.hoisted() to create mocks outside the vi.mock scope
const { mockFromFn } = vi.hoisted(() => {
  return {
    mockFromFn: vi.fn(),
  }
})

vi.mock('@supabase/supabase-js', () => ({
  createClient: vi.fn(() => ({
    from: mockFromFn,
  })),
}))

// Now we can safely import
import { getActiveSubreddits, getSettings, upsertIdea } from '../../src/db/supabase.js'

describe('getActiveSubreddits', () => {
  beforeEach(() => {
    mockFromFn.mockClear()
  })

  it('returns active subreddits', async () => {
    const subreddits = [
      { id: 'sub1', name: 'SomebodyMakeThis', active: true, added_at: '2026-01-01' },
    ]
    mockFromFn.mockReturnValueOnce({
      select: vi.fn().mockReturnThis(),
      eq: vi.fn().mockResolvedValueOnce({ data: subreddits, error: null }),
    })

    const result = await getActiveSubreddits()
    expect(result).toEqual(subreddits)
  })
})

describe('getSettings', () => {
  beforeEach(() => {
    mockFromFn.mockClear()
  })

  it('returns settings row', async () => {
    const settings = {
      id: 's1',
      ollama_api_key: 'k',
      ollama_model: 'llama3.2',
      min_upvotes_threshold: 10,
      scraper_base_url: 'http://localhost:3000',
    }
    mockFromFn.mockReturnValueOnce({
      select: vi.fn().mockReturnThis(),
      single: vi.fn().mockResolvedValueOnce({ data: settings, error: null }),
    })

    const result = await getSettings()
    expect(result.ollama_model).toBe('llama3.2')
  })
})

describe('upsertIdea', () => {
  beforeEach(() => {
    mockFromFn.mockClear()
  })

  it('upserts without error', async () => {
    mockFromFn.mockReturnValueOnce({
      upsert: vi.fn().mockResolvedValueOnce({ error: null }),
    })

    await expect(
      upsertIdea({
        reddit_post_id: 'abc',
        subreddit_id: 'sub1',
        title: 'Test',
        body_excerpt: 'Body',
        url: 'https://reddit.com',
        author: 'user',
        reddit_score: 42,
        ai_relevance_score: 80,
        ai_summary: 'Summary',
        ai_category: 'SaaS',
      })
    ).resolves.not.toThrow()
  })
})
