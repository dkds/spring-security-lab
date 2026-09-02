import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { handleCallback } from '../auth/authService'
import { useAuth } from '../auth/AuthContext'

/**
 * OAuth2 redirect callback page.
 *
 * Spring Authorization Server redirects here with ?code=...&state=...
 * after the user authenticates. This page exchanges the code for tokens
 * and then navigates to the originally requested route.
 */
export default function CallbackPage() {
  const navigate = useNavigate()
  const { onTokensReceived } = useAuth()
  const [error, setError] = useState<string | null>(null)
  const handled = useRef(false) // prevent double-invocation in React StrictMode

  useEffect(() => {
    if (handled.current) return
    handled.current = true

    const params = new URLSearchParams(window.location.search)

    handleCallback(params)
      .then((returnTo) => {
        onTokensReceived()
        navigate(returnTo, { replace: true })
      })
      .catch((err: unknown) => {
        console.error('Callback error', err)
        setError(err instanceof Error ? err.message : 'Authentication failed')
      })
  }, [navigate, onTokensReceived])

  if (error) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50">
        <div className="max-w-md rounded-2xl bg-white p-10 shadow-md text-center">
          <h1 className="text-xl font-semibold text-red-600 mb-2">Authentication Error</h1>
          <p className="text-sm text-gray-500 mb-6">{error}</p>
          <a
            href="/login"
            className="inline-block rounded-lg bg-indigo-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-indigo-700 transition-colors"
          >
            Try again
          </a>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <div className="flex flex-col items-center gap-4 rounded-2xl bg-white p-10 shadow-md">
        <div className="h-10 w-10 animate-spin rounded-full border-4 border-indigo-500 border-t-transparent" />
        <p className="text-sm text-gray-500">Completing sign-in&hellip;</p>
      </div>
    </div>
  )
}
