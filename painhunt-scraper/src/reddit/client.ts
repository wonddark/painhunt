export type RedditPost = {
  id: string
  title: string
  selftext: string
  author: string
  score: number
  permalink: string
}

type RedditListing = {
  data: { children: Array<{ data: RedditPost }> }
}

async function fetchWithRetry(url: string, token: string, attempt = 0): Promise<Response> {
  const res = await fetch(url, {
    headers: {
      Authorization: `Bearer ${token}`,
      'User-Agent': 'painhunt/1.0 (personal tool)',
    },
  })
  if (res.status === 429 && attempt < 3) {
    const delay = Math.pow(2, attempt) * 1000 // 1s, 2s, 4s
    await new Promise((r) => setTimeout(r, delay))
    return fetchWithRetry(url, token, attempt + 1)
  }
  return res
}

async function fetchListing(subreddit: string, sort: 'new' | 'hot', token: string): Promise<RedditPost[]> {
  const url = `https://oauth.reddit.com/r/${subreddit}/${sort}.json?limit=100`
  const res = await fetchWithRetry(url, token)

  if (!res.ok) {
    throw new Error(`Reddit fetch failed: ${res.status}`)
  }

  const data = (await res.json()) as RedditListing
  return data.data.children.map((c) => c.data)
}

export async function fetchSubredditPosts(subreddit: string, token: string): Promise<RedditPost[]> {
  const [newPosts, hotPosts] = await Promise.all([
    fetchListing(subreddit, 'new', token),
    fetchListing(subreddit, 'hot', token),
  ])

  const seen = new Set<string>()
  const all: RedditPost[] = []
  for (const post of [...newPosts, ...hotPosts]) {
    if (!seen.has(post.id)) {
      seen.add(post.id)
      all.push(post)
    }
  }
  return all
}
