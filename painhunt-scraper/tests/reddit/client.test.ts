import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fetchSubredditPosts, parseRedditJson } from '../../src/reddit/client.js'

vi.stubGlobal('fetch', vi.fn())

const atomFeed = (ids: string[]) => `<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom">
${ids.map((id) => `  <entry>
    <id>t3_${id}</id>
    <title>Post ${id}</title>
    <content></content>
    <author><name>/u/user1</name></author>
    <link href="https://www.reddit.com/r/test/comments/${id}/post_${id}/"/>
  </entry>`).join('\n')}
</feed>`

describe('fetchSubredditPosts', () => {
  beforeEach(() => vi.mocked(fetch).mockReset())

  it('returns deduplicated posts from new and hot', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce({ ok: true, text: async () => atomFeed(['a1', 'b1']) } as Response)
      .mockResolvedValueOnce({ ok: true, text: async () => atomFeed(['a1', 'c1']) } as Response)

    const posts = await fetchSubredditPosts('testsubreddit')

    expect(posts).toHaveLength(3) // a1, b1, c1 — a1 deduplicated
  })

  it('throws on non-ok response', async () => {
    vi.mocked(fetch).mockResolvedValue({ ok: false, status: 403 } as Response)

    await expect(fetchSubredditPosts('sub')).rejects.toThrow('Reddit fetch failed: 403')
  })
})

describe('parseRedditJson', () => {
  const makeChild = (id: string, subreddit = 'entrepreneur') => ({
    data: {
      id,
      title: `Post ${id}`,
      selftext: 'body text',
      author: 'alice',
      score: 42,
      permalink: `/r/${subreddit}/comments/${id}/post_${id}/`,
      subreddit,
    },
  })

  it('maps Reddit JSON children to RedditPost array', () => {
    const json = { data: { children: [makeChild('abc'), makeChild('def')] } }
    const { posts, subredditName } = parseRedditJson(json)

    expect(posts).toHaveLength(2)
    expect(posts[0]).toEqual({
      id: 'abc',
      title: 'Post abc',
      selftext: 'body text',
      author: 'alice',
      score: 42,
      permalink: '/r/entrepreneur/comments/abc/post_abc/',
    })
    expect(subredditName).toBe('entrepreneur')
  })

  it('deduplicates posts by id', () => {
    const json = { data: { children: [makeChild('dup'), makeChild('dup'), makeChild('uniq')] } }
    const { posts } = parseRedditJson(json)
    expect(posts).toHaveLength(2)
  })

  it('returns empty posts and empty subredditName when data.children is missing', () => {
    const { posts, subredditName } = parseRedditJson({ kind: 'Listing' })
    expect(posts).toHaveLength(0)
    expect(subredditName).toBe('')
  })

  it('returns empty posts when children is an empty array', () => {
    const { posts, subredditName } = parseRedditJson({ data: { children: [] } })
    expect(posts).toHaveLength(0)
    expect(subredditName).toBe('')
  })
})
