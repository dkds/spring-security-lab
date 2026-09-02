import { createContext, useContext, useState, useCallback, type ReactNode } from 'react'
import { isAuthenticated, getAccessToken } from './storage'
import { startLogin, logout as serviceLogout } from './authService'

interface AuthContextValue {
  /** True when a valid access token is held in memory. */
  authenticated: boolean
  /** Trigger the OAuth2 authorization code + PKCE flow. */
  login: (returnTo?: string) => Promise<void>
  /** Clear tokens and redirect to auth-server end-session endpoint. */
  logout: () => void
  /** Returns the current access token (or null if unauthenticated/expired). */
  getToken: () => string | null
  /** Called by CallbackPage after a successful token exchange. */
  onTokensReceived: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [authenticated, setAuthenticated] = useState<boolean>(isAuthenticated)

  const login = useCallback(async (returnTo?: string) => {
    await startLogin(returnTo)
  }, [])

  const logout = useCallback(() => {
    serviceLogout()
    setAuthenticated(false)
  }, [])

  const getToken = useCallback(() => getAccessToken(), [])

  const onTokensReceived = useCallback(() => {
    setAuthenticated(true)
  }, [])

  return (
    <AuthContext.Provider value={{ authenticated, login, logout, getToken, onTokensReceived }}>
      {children}
    </AuthContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
