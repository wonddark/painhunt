import { describe, it, expect, vi } from 'vitest'
import { buildChatMessages } from '../src/ai/ideaChat.js'

vi.mock('../src/db/supabase.js', () => ({
  getIdeaById: vi.fn(),
  getSettings: vi.fn(),
}))

describe('buildChatMessages', () => {
  it('prepends system message containing all idea context fields', () => {
    const idea = {
      title: 'My Idea',
      ai_summary: 'A great pain point',
      body_excerpt: 'Users struggle with X every day',
    }
    const messages = [{ role: 'user' as const, content: 'Tell me more' }]

    const result = buildChatMessages(idea, messages)

    expect(result).toHaveLength(2)
    expect(result[0].role).toBe('system')
    expect(result[0].content).toContain('My Idea')
    expect(result[0].content).toContain('A great pain point')
    expect(result[0].content).toContain('Users struggle with X every day')
    expect(result[1]).toEqual({ role: 'user', content: 'Tell me more' })
  })

  it('handles null body_excerpt without emitting the word "null"', () => {
    const idea = { title: 'T', ai_summary: 'S', body_excerpt: null }
    const result = buildChatMessages(idea, [{ role: 'user', content: 'Hi' }])
    expect(result[0].content).not.toContain('null')
  })

  it('preserves full message history order after system message', () => {
    const idea = { title: 'T', ai_summary: 'S', body_excerpt: null }
    const messages = [
      { role: 'user' as const, content: 'First' },
      { role: 'assistant' as const, content: 'Second' },
      { role: 'user' as const, content: 'Third' },
    ]
    const result = buildChatMessages(idea, messages)
    expect(result).toHaveLength(4)
    expect(result[1].content).toBe('First')
    expect(result[2].content).toBe('Second')
    expect(result[3].content).toBe('Third')
  })
})
