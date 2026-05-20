const PAIN_SIGNALS = [
  'wish there was',
  "why isn't there",
  'why is there no',
  'someone should build',
  'tired of',
  'frustrated with',
  'no good app',
  'manually',
  'waste of time',
  'nobody has built',
  'does anyone know of',
  'is there an app',
]

export function matchesPainSignal(title: string, body: string): boolean {
  const text = `${title} ${body}`.toLowerCase()
  return PAIN_SIGNALS.some((signal) => text.includes(signal))
}
