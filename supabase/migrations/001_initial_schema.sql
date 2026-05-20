-- painhunt/supabase/migrations/001_initial_schema.sql

create table settings (
  id uuid primary key default gen_random_uuid(),
  ollama_api_key text not null default '',
  ollama_model text not null default 'llama3.2',
  min_upvotes_threshold int not null default 10,
  scraper_base_url text not null default 'http://localhost:3000'
);

-- Enforce singleton pattern at database level
create unique index settings_singleton_idx on settings ((1::int));

-- Seed default row on migration
insert into settings default values;

create table subreddits (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  active boolean not null default true,
  added_at timestamptz not null default now()
);

create table ideas (
  id uuid primary key default gen_random_uuid(),
  reddit_post_id text unique not null,
  subreddit_id uuid references subreddits(id) on delete cascade,
  title text not null,
  body_excerpt text,  -- max 500 chars, enforced by scraper
  url text not null,
  author text not null,
  reddit_score int not null default 0,
  ai_relevance_score int not null check (ai_relevance_score >= 0 and ai_relevance_score <= 100),
  ai_summary text not null,
  ai_category text not null check (ai_category in ('SaaS','Mobile','Hardware','Service','Other')),
  scraped_at timestamptz not null default now()
);

create table bookmarks (
  id uuid primary key default gen_random_uuid(),
  idea_id uuid references ideas(id) on delete cascade,
  saved_at timestamptz not null default now()
);

create table notes (
  id uuid primary key default gen_random_uuid(),
  idea_id uuid unique references ideas(id) on delete cascade,
  content text not null default '',
  tags text[] not null default '{}',
  updated_at timestamptz not null default now()
);

-- Indexes for common query patterns
create index ideas_scraped_at_idx on ideas(scraped_at desc);
create index ideas_ai_relevance_idx on ideas(ai_relevance_score desc);
create index ideas_category_idx on ideas(ai_category);

-- Composite index for primary query pattern (category + sort)
create index ideas_category_sort_idx on ideas(ai_category, scraped_at desc, ai_relevance_score desc);

-- Foreign key indexes (PostgreSQL doesn't auto-create these)
create index ideas_subreddit_id_idx on ideas(subreddit_id);
create index bookmarks_idea_id_idx on bookmarks(idea_id);
create index notes_idea_id_idx on notes(idea_id);
