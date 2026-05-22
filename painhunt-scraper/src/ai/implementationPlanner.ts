export type PlanInput = {
  title: string
  summary: string
  bodyExcerpt: string
  model: string
  apiKey: string
  baseUrl: string
}

export type PlanTask = {
  id: string
  task: string
  done: boolean
}

export type PlanGoal = {
  goal: string
  tasks: PlanTask[]
}

export type PlanResult = {
  concept: string
  description: string
  goals: PlanGoal[]
}

const PROMPT = (title: string, summary: string, bodyExcerpt: string) => `
You are a product and engineering planner.

Idea title: "${title}"
AI summary: "${summary}"
Original post excerpt: "${bodyExcerpt.slice(0, 500)}"

Generate a compact implementation plan. Respond ONLY with valid JSON in this exact format:
{
  "concept": "<one sentence — the core thing being built>",
  "description": "<2-3 sentences on what needs to be done to solve the problem>",
  "goals": [
    {
      "goal": "<measurable goal name>",
      "tasks": [
        { "id": "<uuid>", "task": "<specific measurable task>", "done": false }
      ]
    }
  ]
}

Rules: 2-4 goals. 2-5 tasks per goal. Tasks must be specific and measurable. No vague tasks like "research" or "think about". Generate real UUIDs for each task id.
`.trim()

export async function planImplementation(input: PlanInput): Promise<PlanResult | null> {
  try {
    const res = await fetch(`${input.baseUrl}/api/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${input.apiKey}`,
      },
      body: JSON.stringify({
        model: input.model,
        messages: [{ role: 'user', content: PROMPT(input.title, input.summary, input.bodyExcerpt) }],
        stream: false,
        format: 'json',
      }),
    })

    if (!res.ok) {
      console.warn(`Ollama API error: ${res.status}`)
      return null
    }

    const data = (await res.json()) as { message: { content: string } }
    const raw = data.message.content
      .replace(/^```(?:json)?\s*/i, '')
      .replace(/```\s*$/i, '')
      .trim()
    const parsed = JSON.parse(raw) as PlanResult

    if (
      typeof parsed.concept !== 'string' ||
      typeof parsed.description !== 'string' ||
      !Array.isArray(parsed.goals)
    ) {
      return null
    }

    return parsed
  } catch (e) {
    console.error(e)
    return null
  }
}
