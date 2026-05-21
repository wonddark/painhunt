import { XMLParser } from 'fast-xml-parser'

export type RedditPost = {
  id: string
  title: string
  selftext: string
  author: string
  score: number
  permalink: string
}

type AtomEntry = {
  id: string
  title: string
  content: string
  author: { name: string }
  link: { '@_href': string }
}

type AtomFeed = {
  feed: { entry: AtomEntry | AtomEntry[] }
}

const parser = new XMLParser({ ignoreAttributes: false, attributeNamePrefix: '@_' })

function stripHtml(html: string): string {
  return html.replace(/<[^>]+>/g, '').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&').replace(/&#39;/g, "'").replace(/&quot;/g, '"').trim()
}

async function fetchWithRetry(url: string, attempt = 0): Promise<Response> {
  const res = await fetch(url, {
    headers: {
      'User-Agent': 'script:painhunt:v1.0.0 (by /u/Distinct-Loss-2744)',
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

  const xml = await res.text()
  const feed = parser.parse(xml) as AtomFeed
  const entries = Array.isArray(feed.feed.entry) ? feed.feed.entry : [feed.feed.entry]

  return entries.filter(Boolean).map((entry) => {
    const href: string = entry.link?.['@_href'] ?? ''
    const permalink = href.replace('https://www.reddit.com', '')
    const rawId: string = typeof entry.id === 'string' ? entry.id : String(entry.id)
    const id = rawId.replace('t3_', '').split('_').pop() ?? rawId
    const authorName: string = typeof entry.author?.name === 'string' ? entry.author.name : ''

    return {
      id,
      title: typeof entry.title === 'string' ? entry.title : String(entry.title ?? ''),
      selftext: stripHtml(typeof entry.content === 'string' ? entry.content : String(entry.content ?? '')),
      author: authorName.replace('/u/', ''),
      score: 0,
      permalink,
    }
  })
}

export type ParsedRedditData = {
  posts: RedditPost[]
  subredditName: string
}

export function parseRedditJson(json: unknown): ParsedRedditData {
  const children = (json as any)?.data?.children
  if (!Array.isArray(children) || children.length === 0) {
    return { posts: [], subredditName: '' }
  }

  const seen = new Set<string>()
  const posts: RedditPost[] = []
  let subredditName = ''

  for (const child of children) {
    const d = child?.data
    if (!d || typeof d.id !== 'string') continue
    if (seen.has(d.id)) continue
    seen.add(d.id)
    if (!subredditName && typeof d.subreddit === 'string') {
      subredditName = d.subreddit
    }
    posts.push({
      id: d.id,
      title: typeof d.title === 'string' ? d.title : String(d.title ?? ''),
      selftext: typeof d.selftext === 'string' ? d.selftext : '',
      author: typeof d.author === 'string' ? d.author : '',
      score: typeof d.score === 'number' ? d.score : 0,
      permalink: typeof d.permalink === 'string' ? d.permalink : '',
    })
  }

  return { posts, subredditName }
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
