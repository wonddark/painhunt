import { describe, it, expect, vi, beforeEach } from 'vitest'
import { planImplementation } from '../../src/ai/implementationPlanner.js'

vi.stubGlobal('fetch', vi.fn())

const validGoals = [
  {
    goal: 'Core backend',
    tasks: [
      { id: 'uuid-1', task: 'Set up database schema', done: false },
      { id: 'uuid-2', task: 'Build REST API endpoints', done: false },
    ],
  },
]

const validAiResponse = {
  message: {
    content: JSON.stringify({
      concept: 'An app that tracks expenses automatically.',
      description: 'Build a mobile app that reads bank SMS and categorises spending. Users get a weekly summary.',
      goals: validGoals,
    }),
  },
}

describe('planImplementation', () => {
  beforeEach(() => vi.mocked(fetch).mockReset())

  it('returns parsed plan for valid AI response', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: async () => validAiResponse,
    } as Response)

    const result = await planImplementation({
      title: 'Hate tracking expenses manually',
      summary: 'Users want automatic expense tracking.',
      bodyExcerpt: 'I spend an hour every week on this.',
      model: 'llama3.2',
      apiKey: 'key',
      baseUrl: 'https://api.ollama.com',
    })

    expect(result).toEqual({
      concept: 'An app that tracks expenses automatically.',
      description: 'Build a mobile app that reads bank SMS and categorises spending. Users get a weekly summary.',
      goals: validGoals,
    })
  })

  it('strips markdown fences from AI response', async () => {
    const wrapped = { message: { content: '```json\n' + JSON.stringify({ concept: 'c', description: 'd', goals: validGoals }) + '\n```' } }
    vi.mocked(fetch).mockResolvedValueOnce({ ok: true, json: async () => wrapped } as Response)

    const result = await planImplementation({ title: 't', summary: 's', bodyExcerpt: 'b', model: 'm', apiKey: 'k', baseUrl: 'u' })

    expect(result?.concept).toBe('c')
  })

  it('returns null for invalid JSON', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ message: { content: 'not json at all' } }),
    } as Response)

    const result = await planImplementation({ title: 't', summary: 's', bodyExcerpt: 'b', model: 'm', apiKey: 'k', baseUrl: 'u' })

    expect(result).toBeNull()
  })

  it('returns null when required fields are missing', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ message: { content: JSON.stringify({ concept: 'c' }) } }),
    } as Response)

    const result = await planImplementation({ title: 't', summary: 's', bodyExcerpt: 'b', model: 'm', apiKey: 'k', baseUrl: 'u' })

    expect(result).toBeNull()
  })

  it('returns null on fetch error', async () => {
    vi.mocked(fetch).mockRejectedValueOnce(new Error('Network error'))

    const result = await planImplementation({ title: 't', summary: 's', bodyExcerpt: 'b', model: 'm', apiKey: 'k', baseUrl: 'u' })

    expect(result).toBeNull()
  })

  it('returns null when Ollama returns non-ok status', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({ ok: false, status: 503 } as Response)

    const result = await planImplementation({ title: 't', summary: 's', bodyExcerpt: 'b', model: 'm', apiKey: 'k', baseUrl: 'u' })

    expect(result).toBeNull()
  })
})
