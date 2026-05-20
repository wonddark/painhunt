import { describe, it, expect } from 'vitest'
import { matchesPainSignal } from '../../src/filter/keywords.js'

describe('matchesPainSignal', () => {
  it('matches pain-signal phrase in title', () => {
    expect(matchesPainSignal('I wish there was an app for this', '')).toBe(true)
  })

  it('matches pain-signal phrase in body', () => {
    expect(matchesPainSignal('Random title', 'I am so frustrated with this process')).toBe(true)
  })

  it('is case-insensitive', () => {
    expect(matchesPainSignal("WHY ISN'T THERE a good solution", '')).toBe(true)
  })

  it('returns false when no pain signal', () => {
    expect(matchesPainSignal('Check out this cool project', 'I built something today')).toBe(false)
  })

  it('matches "no good app" phrase', () => {
    expect(matchesPainSignal('There is no good app for this', '')).toBe(true)
  })
})
