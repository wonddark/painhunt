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
  return html.replaceAll(/<[^>]+>/g, '').replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&amp;', '&').replaceAll('&#39;', "'").replaceAll('&quot;', '"').trim()
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

type RedditJsonChild = {
  data?: Record<string, unknown>
}

function getRedditChildren(json: unknown): RedditJsonChild[] {
  const children = (json as { data?: { children?: unknown } })?.data?.children
  return Array.isArray(children) ? children : []
}

function getString(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback
}

function getTitle(value: unknown): string {
  return typeof value === 'string' ? value : String(value ?? '')
}

function getScore(value: unknown): number {
  return typeof value === 'number' ? value : 0
}

function toRedditPost(data: Record<string, unknown>): RedditPost | null {
  if (typeof data.id !== 'string') {
    return null
  }

  return {
    id: data.id,
    title: getTitle(data.title),
    selftext: getString(data.selftext),
    author: getString(data.author),
    score: getScore(data.score),
    permalink: getString(data.permalink),
  }
}

function getSubredditName(currentName: string, data: Record<string, unknown>): string {
  return currentName || getString(data.subreddit)
}

export function parseRedditJson(json: unknown): ParsedRedditData {
  const children = getRedditChildren(json)
  const seen = new Set<string>()
  const posts: RedditPost[] = []
  let subredditName = ''

  for (const child of children) {
    const data = child?.data ??{}
    const post = data ? toRedditPost(data) : null

    if (!post || seen.has(post.id)) {
      continue
    }

    seen.add(post.id)
    subredditName = getSubredditName(subredditName, data)
    posts.push(post)
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
