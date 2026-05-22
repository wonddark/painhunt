import { createClient } from '@supabase/supabase-js'
import type { PlanGoal } from '../ai/implementationPlanner.js'

const client = createClient(
  process.env.SUPABASE_URL!,
  process.env.SUPABASE_SERVICE_ROLE_KEY!
)

export type ImplementationInsert = {
  idea_id: string
  concept: string
  description: string
  goals: PlanGoal[]
}

export type Implementation = ImplementationInsert & {
  id: string
  created_at: string
}

function throwDbError(error: { message: string }): never {
  throw new Error(error.message)
}

export async function createImplementation(data: ImplementationInsert): Promise<Implementation> {
  const { data: existing } = await client
    .from('implementations')
    .select()
    .eq('idea_id', data.idea_id)
    .maybeSingle()

  if (existing) return existing as Implementation

  const { data: inserted, error } = await client
    .from('implementations')
    .insert(data)
    .select()
    .single()

  if (error) throwDbError(error)
  return inserted as Implementation
}
