-- supabase/migrations/003_chat_messages.sql
CREATE TABLE chat_messages (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  idea_id    uuid NOT NULL REFERENCES ideas(id) ON DELETE CASCADE,
  role       text NOT NULL CHECK (role IN ('user', 'assistant')),
  content    text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX chat_messages_idea_id_idx ON chat_messages(idea_id, created_at);
