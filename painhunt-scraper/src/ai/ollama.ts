export type ScoreInput = {
  title: string
  body: string
  model: string
  apiKey: string
  baseUrl: string
}

export type ScoreResult = {
  relevanceScore: number
  summary: string
  category: string
}

const VALID_CATEGORIES = new Set(['SaaS', 'Mobile', 'Hardware', 'Service', 'Other'])

const PROMPT = (title: string, body: string) => `
You are analyzing a Reddit post to determine if it describes a genuine business pain point.

Post title: "${title}"
Post body: "${body.slice(0, 800)}"

Respond ONLY with valid JSON in this exact format:
{
  "relevance_score": <integer 0-100>,
  "summary": "<2 sentences describing the pain point and why it's a business opportunity>",
  "category": "<one of: SaaS, Mobile, Hardware, Service, Other>"
}

Score 0-39: not a meaningful pain point or business opportunity.
Score 40-69: moderate pain point with some opportunity.
Score 70-100: clear, specific pain point with strong business opportunity.
`.trim()

export async function scorePost(input: ScoreInput): Promise<ScoreResult | null> {
  try {
    const res = await fetch(`${input.baseUrl}/api/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${input.apiKey}`,
      },
      body: JSON.stringify({
        model: input.model,
        messages: [{ role: 'user', content: PROMPT(input.title, input.body) }],
        stream: false,
        format: 'json',
      }),
    })

    if (!res.ok) {
      console.warn(`Ollama API error: ${res.status}`)
      return null
    }

    const data = (await res.json()) as { message: { content: string } }
    const raw = data.message.content.replace(/^```(?:json)?\s*/i, '').replace(/```\s*$/i, '').trim()
    const parsed = JSON.parse(raw) as {
      relevance_score: number
      summary: string
      category: string
    }

    if (
      typeof parsed.relevance_score !== 'number' ||
      typeof parsed.summary !== 'string' ||
      !VALID_CATEGORIES.has(parsed.category)
    ) {
      return null
    }

    return {
      relevanceScore: Math.max(0, Math.min(100, Math.round(parsed.relevance_score))),
      summary: parsed.summary,
      category: parsed.category,
    }
  } catch (e) {
    console.error(e)
    console.warn('Ollama scoring failed, discarding post')
    return null
  }
}
