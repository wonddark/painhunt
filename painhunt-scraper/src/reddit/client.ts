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

async function fetchWithRetry(url: string, attempt = 0): Promise<Response> {
  const res = await fetch(url, {
    headers: {
      'User-Agent': 'painhunt/1.0 (personal tool)',
    },
  })
  if (res.status === 429 && attempt < 3) {
    const delay = Math.pow(2, attempt) * 1000
    await new Promise((r) => setTimeout(r, delay))
    return fetchWithRetry(url, attempt + 1)
  }
  return res
}

async function fetchListing(subreddit: string, sort: 'new' | 'hot'): Promise<RedditPost[]> {
  const url = `https://www.reddit.com/r/${subreddit}/${sort}.json?limit=100`
  const res = await fetchWithRetry(url)

  if (!res.ok) {
    throw new Error(`Reddit fetch failed: ${res.status}`)
  }

  const data = (await res.json()) as RedditListing
  return data.data.children.map((c) => c.data)
}

export async function fetchSubredditPosts(subreddit: string): Promise<RedditPost[]> {
  const [newPosts, hotPosts] = await Promise.all([
    fetchListing(subreddit, 'new'),
    fetchListing(subreddit, 'hot'),
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
