import { describe, it, expect, vi, beforeEach } from 'vitest'
import { scorePost } from '../../src/ai/ollama.js'

vi.stubGlobal('fetch', vi.fn())

describe('scorePost', () => {
  beforeEach(() => vi.mocked(fetch).mockReset())

  it('returns parsed AI result for valid response', async () => {
    const aiResponse = {
      message: {
        content: JSON.stringify({
          relevance_score: 75,
          summary: 'User frustrated with manual expense tracking. No good mobile solution exists.',
          category: 'Mobile',
        }),
      },
    }
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: async () => aiResponse,
    } as Response)

    const result = await scorePost({
      title: 'hate tracking expenses',
      body: 'manually every day',
      model: 'llama3.2',
      apiKey: 'key',
      baseUrl: 'https://api.ollama.com',
    })

    expect(result).toEqual({
      relevanceScore: 75,
      summary: 'User frustrated with manual expense tracking. No good mobile solution exists.',
      category: 'Mobile',
    })
  })

  it('returns null for invalid JSON response', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ message: { content: 'not json' } }),
    } as Response)

    const result = await scorePost({
      title: 'title',
      body: 'body',
      model: 'llama3.2',
      apiKey: 'key',
      baseUrl: 'https://api.ollama.com',
    })

    expect(result).toBeNull()
  })

  it('returns null on fetch error', async () => {
    vi.mocked(fetch).mockRejectedValueOnce(new Error('Network error'))

    const result = await scorePost({
      title: 'title',
      body: 'body',
      model: 'llama3.2',
      apiKey: 'key',
      baseUrl: 'https://api.ollama.com',
    })

    expect(result).toBeNull()
  })
})
