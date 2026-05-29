-- painhunt/supabase/migrations/004_idea_visibility.sql

-- Whether an idea shows in the default feed. Hidden ideas stay in the table
-- with visible = false and are only shown when the user opts to view hidden.
alter table ideas add column visible boolean not null default true;

-- Partial index: the only selective query is fetching the (few) hidden ideas.
create index ideas_hidden_idx on ideas(visible) where visible = false;
