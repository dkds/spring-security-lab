/**
 * High-level OAuth2 Authorization Code + PKCE flow.
 */
import { AUTH_CONFIG } from './config'
import { generateCodeVerifier, generateCodeChallenge, generateState } from './pkce'
import { saveHandshake, loadHandshake, clearHandshake, saveTokens, clearTokens, getIdToken } from './storage'

/**
 * Kick off the authorization code + PKCE flow.
 * Saves handshake values and redirects the browser to the auth server.
 */
export async function startLogin(returnTo: string = '/dashboard') {
  const codeVerifier = generateCodeVerifier()
  const codeChallenge = await generateCodeChallenge(codeVerifier)
  const state = generateState()

  saveHandshake(codeVerifier, state, returnTo)

  const params = new URLSearchParams({
    response_type: 'code',
    client_id: AUTH_CONFIG.clientId,
    redirect_uri: AUTH_CONFIG.redirectUri,
    scope: AUTH_CONFIG.scope,
    state,
    code_challenge: codeChallenge,
    code_challenge_method: 'S256',
  })

  window.location.href = `${AUTH_CONFIG.authorizationEndpoint}?${params}`
}

/**
 * Handle the callback from the authorization server.
 * Validates state, exchanges the code for tokens, and returns the path to
 * redirect back to.
 */
export async function handleCallback(searchParams: URLSearchParams): Promise<string> {
  const code = searchParams.get('code')
  const returnedState = searchParams.get('state')
  const error = searchParams.get('error')

  if (error) {
    throw new Error(`Authorization error: ${error} — ${searchParams.get('error_description') ?? ''}`)
  }

  if (!code || !returnedState) {
    throw new Error('Missing code or state in callback')
  }

  const handshake = loadHandshake()
  if (!handshake) {
    throw new Error('No PKCE handshake found in session — possible replay attack')
  }

  if (returnedState !== handshake.state) {
    clearHandshake()
    throw new Error('State mismatch — possible CSRF attack')
  }

  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    client_id: AUTH_CONFIG.clientId,
    redirect_uri: AUTH_CONFIG.redirectUri,
    code,
    code_verifier: handshake.codeVerifier,
  })

  const response = await fetch(AUTH_CONFIG.tokenEndpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: body.toString(),
  })

  if (!response.ok) {
    const detail = await response.text()
    throw new Error(`Token exchange failed (${response.status}): ${detail}`)
  }

  const json = await response.json()
  const expiresIn: number = json.expires_in ?? 3600

  saveTokens({
    accessToken: json.access_token,
    idToken: json.id_token,
    expiresAt: Date.now() + expiresIn * 1000,
  })

  const returnTo = handshake.returnTo
  clearHandshake()
  return returnTo
}

/**
 * Log out: clear local tokens and redirect to the auth server's end-session
 * endpoint (OIDC RP-initiated logout).
 */
export function logout() {
  const idToken = getIdToken()
  clearTokens()

  const params = new URLSearchParams({
    client_id: AUTH_CONFIG.clientId,
    post_logout_redirect_uri: window.location.origin,
  })
  if (idToken) params.set('id_token_hint', idToken)

  window.location.href = `${AUTH_CONFIG.endSessionEndpoint}?${params}`
}
