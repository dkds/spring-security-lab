/**
 * Thin wrapper around sessionStorage for OAuth2 state.
 *
 * Tokens are kept in memory (module-level) so they are never persisted to
 * disk. SessionStorage is only used for the short-lived PKCE handshake values
 * that must survive the redirect.
 */

const KEYS = {
  codeVerifier: 'pkce_code_verifier',
  state: 'oauth_state',
  returnTo: 'oauth_return_to',
} as const

// ---------------------------------------------------------------------------
// PKCE handshake (sessionStorage — survives redirect, cleared on tab close)
// ---------------------------------------------------------------------------

export function saveHandshake(codeVerifier: string, state: string, returnTo: string) {
  sessionStorage.setItem(KEYS.codeVerifier, codeVerifier)
  sessionStorage.setItem(KEYS.state, state)
  sessionStorage.setItem(KEYS.returnTo, returnTo)
}

export function loadHandshake(): { codeVerifier: string; state: string; returnTo: string } | null {
  const codeVerifier = sessionStorage.getItem(KEYS.codeVerifier)
  const state = sessionStorage.getItem(KEYS.state)
  const returnTo = sessionStorage.getItem(KEYS.returnTo)
  if (!codeVerifier || !state || !returnTo) return null
  return { codeVerifier, state, returnTo }
}

export function clearHandshake() {
  sessionStorage.removeItem(KEYS.codeVerifier)
  sessionStorage.removeItem(KEYS.state)
  sessionStorage.removeItem(KEYS.returnTo)
}

// ---------------------------------------------------------------------------
// Tokens (in-memory only — lost on page reload, which forces re-auth)
// ---------------------------------------------------------------------------

interface TokenSet {
  accessToken: string
  idToken?: string
  expiresAt: number // epoch ms
}

let _tokens: TokenSet | null = null

export function saveTokens(tokens: TokenSet) {
  _tokens = tokens
}

export function getAccessToken(): string | null {
  if (!_tokens) return null
  if (Date.now() >= _tokens.expiresAt) {
    _tokens = null
    return null
  }
  return _tokens.accessToken
}

export function getIdToken(): string | null {
  return _tokens?.idToken ?? null
}

export function clearTokens() {
  _tokens = null
}

export function isAuthenticated(): boolean {
  return getAccessToken() !== null
}
