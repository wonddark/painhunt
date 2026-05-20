import { describe, it, expect, vi } from 'vitest'

const mockFrom = vi.fn()
const mockClient = { from: mockFrom }
vi.mock('@supabase/supabase-js', () => ({
  createClient: vi.fn(() => mockClient),
}))

describe('getActiveSubreddits', () => {
  it('returns active subreddits', async () => {
    const subreddits = [
      { id: 'sub1', name: 'SomebodyMakeThis', active: true, added_at: '2026-01-01' },
    ]
    mockFrom.mockReturnValueOnce({
      select: vi.fn().mockReturnThis(),
      eq: vi.fn().mockResolvedValueOnce({ data: subreddits, error: null }),
    })

    const { getActiveSubreddits } = await import('../../src/db/supabase.js')
    const result = await getActiveSubreddits()
    expect(result).toEqual(subreddits)
  })
})

describe('getSettings', () => {
  it('returns settings row', async () => {
    const settings = { id: 's1', ollama_api_key: 'k', ollama_model: 'llama3.2', min_upvotes_threshold: 10, scraper_base_url: 'http://localhost:3000' }
    mockFrom.mockReturnValueOnce({
      select: vi.fn().mockReturnThis(),
      single: vi.fn().mockResolvedValueOnce({ data: settings, error: null }),
    })

    const { getSettings } = await import('../../src/db/supabase.js')
    const result = await getSettings()
    expect(result.ollama_model).toBe('llama3.2')
  })
})

describe('upsertIdea', () => {
  it('upserts without error', async () => {
    mockFrom.mockReturnValueOnce({
      upsert: vi.fn().mockResolvedValueOnce({ error: null }),
    })

    const { upsertIdea } = await import('../../src/db/supabase.js')
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
