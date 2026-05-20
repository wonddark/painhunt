import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fetchSubredditPosts } from '../../src/reddit/client.js'

vi.stubGlobal('fetch', vi.fn())

const mockPost = (id: string, score: number, title: string, selftext = '') => ({
  data: { id, score, title, selftext, author: 'user1', permalink: `/r/test/comments/${id}` },
})

describe('fetchSubredditPosts', () => {
  beforeEach(() => vi.mocked(fetch).mockReset())

  it('returns deduplicated posts from new and hot', async () => {
    const newPosts = { data: { children: [mockPost('a1', 20, 'Post A'), mockPost('b1', 5, 'Post B')] } }
    const hotPosts = { data: { children: [mockPost('a1', 20, 'Post A'), mockPost('c1', 50, 'Post C')] } }

    vi.mocked(fetch)
      .mockResolvedValueOnce({ ok: true, json: async () => newPosts } as Response)
      .mockResolvedValueOnce({ ok: true, json: async () => hotPosts } as Response)

    const posts = await fetchSubredditPosts('testsubreddit', 'tok_123')

    expect(posts).toHaveLength(3) // a1, b1, c1 — a1 deduplicated
  })

  it('throws on non-ok response', async () => {
    vi.mocked(fetch).mockResolvedValue({ ok: false, status: 403 } as Response)

    await expect(fetchSubredditPosts('sub', 'tok')).rejects.toThrow('Reddit fetch failed: 403')
  })
})
