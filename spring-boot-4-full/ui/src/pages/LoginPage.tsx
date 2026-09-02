import { useEffect } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

/**
 * Public login page.
 *
 * If the user is already authenticated, redirect them straight to /dashboard.
 * Otherwise, immediately kick off the OAuth2 authorization code + PKCE flow.
 * The auth-server will present its own login form (and OTT / SAML2 options).
 */
export default function LoginPage() {
  const { authenticated, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from: string = (location.state as { from?: { pathname: string } })?.from?.pathname ?? '/dashboard'

  useEffect(() => {
    if (authenticated) {
      navigate(from, { replace: true })
    } else {
      login(from)
    }
  }, [authenticated, from, login, navigate])

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <div className="flex flex-col items-center gap-4 rounded-2xl bg-white p-10 shadow-md">
        <div className="h-10 w-10 animate-spin rounded-full border-4 border-indigo-500 border-t-transparent" />
        <p className="text-sm text-gray-500">Redirecting to login&hellip;</p>
      </div>
    </div>
  )
}
