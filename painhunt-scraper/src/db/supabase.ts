import { createClient } from '@supabase/supabase-js'

const client = createClient(
  process.env.SUPABASE_URL!,
  process.env.SUPABASE_SERVICE_ROLE_KEY!
)

export type Subreddit = {
  id: string
  name: string
  active: boolean
  added_at: string
}

export type Settings = {
  id: string
  ollama_api_key: string
  ollama_model: string
  min_upvotes_threshold: number
  scraper_base_url: string
}

export type IdeaInsert = {
  reddit_post_id: string
  subreddit_id: string
  title: string
  body_excerpt: string
  url: string
  author: string
  reddit_score: number
  ai_relevance_score: number
  ai_summary: string
  ai_category: string
}

function throwDbError(error: { message: string }): never {
  throw new Error(error.message)
}

export async function getActiveSubreddits(): Promise<Subreddit[]> {
  const { data, error } = await client
    .from('subreddits')
    .select()
    .eq('active', true)
  if (error) throwDbError(error)
  return data as Subreddit[]
}

export async function getSettings(): Promise<Settings> {
  const { data, error } = await client
    .from('settings')
    .select()
    .single()
  if (error) throwDbError(error)
  return data as Settings
}

export async function upsertIdea(idea: IdeaInsert): Promise<void> {
  const { error } = await client
    .from('ideas')
    .upsert(idea, { onConflict: 'reddit_post_id', ignoreDuplicates: true })
  if (error) throwDbError(error)
}
