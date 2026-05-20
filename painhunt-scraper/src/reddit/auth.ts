export type RedditCredentials = {
  clientId: string
  clientSecret: string
  username: string
  password: string
}

export async function getRedditToken(creds: RedditCredentials): Promise<string> {
  const basic = Buffer.from(`${creds.clientId}:${creds.clientSecret}`).toString('base64')
  const body = new URLSearchParams({
    grant_type: 'password',
    username: creds.username,
    password: creds.password,
  })

  const res = await fetch('https://www.reddit.com/api/v1/access_token', {
    method: 'POST',
    headers: {
      Authorization: `Basic ${basic}`,
      'Content-Type': 'application/x-www-form-urlencoded',
      'User-Agent': 'painhunt/1.0 (personal tool)',
    },
    body,
  })

  if (!res.ok) {
    throw new Error(`Reddit auth failed: ${res.status}`)
  }

  const data = (await res.json()) as { access_token: string }
  return data.access_token
}
