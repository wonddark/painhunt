CREATE TABLE implementations (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  idea_id      uuid NOT NULL UNIQUE REFERENCES ideas(id) ON DELETE CASCADE,
  concept      text NOT NULL,
  description  text NOT NULL,
  goals        jsonb NOT NULL DEFAULT '[]',
  created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX implementations_created_at_idx ON implementations(created_at DESC);
