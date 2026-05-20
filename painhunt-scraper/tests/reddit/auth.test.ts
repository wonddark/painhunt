import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.stubGlobal('fetch', vi.fn())

describe('getRedditToken', () => {
  beforeEach(() => {
    vi.mocked(fetch).mockReset()
  })

  it('returns access token on success', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ access_token: 'tok_123', expires_in: 86400 }),
    } as Response)

    const { getRedditToken } = await import('../../src/reddit/auth.js')
    const token = await getRedditToken({
      clientId: 'id',
      clientSecret: 'secret',
      username: 'user',
      password: 'pass',
    })

    expect(token).toBe('tok_123')
  })

  it('throws on non-ok response', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: false,
      status: 401,
      text: async () => 'Unauthorized',
    } as Response)

    const { getRedditToken } = await import('../../src/reddit/auth.js')
    await expect(
      getRedditToken({ clientId: 'id', clientSecret: 'secret', username: 'u', password: 'p' })
    ).rejects.toThrow('Reddit auth failed: 401')
  })
})
