import type { Response } from 'express'
import { getIdeaById, getSettings } from '../db/supabase.js'
import type { ChatIdeaContext } from '../db/supabase.js'

export type ChatMessageInput = { role: 'user' | 'assistant'; content: string }

export function buildChatMessages(
  idea: ChatIdeaContext,
  userMessages: ChatMessageInput[],
): Array<{ role: string; content: string }> {
  const systemMessage = {
    role: 'system',
    content: `You are an expert advisor discussing a business idea found on Reddit.
Title: "${idea.title}"
Summary: "${idea.ai_summary}"
Original post excerpt: "${idea.body_excerpt ?? ''}"
Help the user think deeply about this idea.`.trim(),
  }
  return [systemMessage, ...userMessages]
}

export async function streamIdeaChat(
  ideaId: string,
  messages: ChatMessageInput[],
  res: Response,
): Promise<void> {
  const [idea, settings] = await Promise.all([getIdeaById(ideaId), getSettings()])
  const fullMessages = buildChatMessages(idea, messages)

  const ollamaBaseUrl = process.env.OLLAMA_BASE_URL ?? 'https://api.ollama.com'
  const ollamaRes = await fetch(`${ollamaBaseUrl}/api/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${settings.ollama_api_key}`,
    },
    body: JSON.stringify({
      model: settings.ollama_model,
      messages: fullMessages,
      stream: true,
    }),
  })

  if (!ollamaRes.ok || !ollamaRes.body) {
    throw new Error(`Ollama error: ${ollamaRes.status}`)
  }

  res.setHeader('Content-Type', 'text/event-stream')
  res.setHeader('Cache-Control', 'no-cache')
  res.setHeader('Connection', 'keep-alive')

  const reader = ollamaRes.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''
    for (const line of lines) {
      if (line.trim()) res.write(`data: ${line}\n\n`)
    }
  }

  if (buffer.trim()) res.write(`data: ${buffer}\n\n`)
  res.write('data: [DONE]\n\n')
  res.end()
}
