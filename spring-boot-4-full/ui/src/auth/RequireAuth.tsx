import { useEffect } from 'react'
import { useLocation, Navigate } from 'react-router-dom'
import { useAuth } from './AuthContext'

interface RequireAuthProps {
  children: React.ReactNode
}

/**
 * Route guard. If the user is not authenticated, starts the OAuth2 login
 * flow with the current path saved as the post-login return destination.
 */
export function RequireAuth({ children }: RequireAuthProps) {
  const { authenticated, login } = useAuth()
  const location = useLocation()

  useEffect(() => {
    if (!authenticated) {
      const returnTo = location.pathname + location.search
      login(returnTo)
    }
  }, [authenticated, login, location])

  if (!authenticated) {
    // Show nothing while the redirect is in flight.
    // Fallback to /login for environments where the redirect is slow.
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return <>{children}</>
}
