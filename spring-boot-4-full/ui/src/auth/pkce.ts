/**
 * PKCE (Proof Key for Code Exchange) helpers — RFC 7636.
 * Uses the Web Crypto API; no third-party library needed.
 */

/** Generate a cryptographically random code verifier (43-128 chars, URL-safe). */
export function generateCodeVerifier(): string {
  const array = new Uint8Array(48)
  crypto.getRandomValues(array)
  return base64UrlEncode(array)
}

/** Derive the S256 code challenge from a verifier. */
export async function generateCodeChallenge(verifier: string): Promise<string> {
  const encoder = new TextEncoder()
  const data = encoder.encode(verifier)
  const digest = await crypto.subtle.digest('SHA-256', data)
  return base64UrlEncode(new Uint8Array(digest))
}

/** Generate a random state parameter to prevent CSRF. */
export function generateState(): string {
  const array = new Uint8Array(16)
  crypto.getRandomValues(array)
  return base64UrlEncode(array)
}

function base64UrlEncode(buffer: Uint8Array): string {
  return btoa(String.fromCharCode(...buffer))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
}
