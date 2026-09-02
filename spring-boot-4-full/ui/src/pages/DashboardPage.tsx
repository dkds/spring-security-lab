import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { fetchProfile } from '../api/resourceClient'

/**
 * Private dashboard page — only reachable when authenticated (guarded by
 * RequireAuth in the router). Demonstrates a real API call to the
 * resource-server using the Bearer token.
 */
export default function DashboardPage() {
  const { logout, getToken } = useAuth()
  const [profile, setProfile] = useState<Record<string, unknown> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const token = getToken()
    if (!token) return

    fetchProfile(token)
      .then((data) => setProfile(data))
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to load profile'))
      .finally(() => setLoading(false))
  }, [getToken])

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Nav */}
      <nav className="bg-white shadow-sm">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-6 py-4">
          <span className="text-lg font-semibold text-indigo-700">Portal</span>
          <button
            onClick={logout}
            className="rounded-lg border border-gray-200 px-4 py-1.5 text-sm text-gray-600 hover:bg-gray-50 transition-colors"
          >
            Sign out
          </button>
        </div>
      </nav>

      {/* Content */}
      <main className="mx-auto max-w-5xl px-6 py-10">
        <h1 className="text-2xl font-bold text-gray-900 mb-6">Dashboard</h1>

        <div className="rounded-2xl bg-white p-8 shadow-sm border border-gray-100">
          <h2 className="text-base font-medium text-gray-700 mb-4">Your profile</h2>

          {loading && (
            <div className="flex items-center gap-3 text-sm text-gray-400">
              <div className="h-5 w-5 animate-spin rounded-full border-2 border-indigo-400 border-t-transparent" />
              Loading from resource-server&hellip;
            </div>
          )}

          {error && (
            <div className="rounded-lg bg-red-50 border border-red-100 px-4 py-3 text-sm text-red-600">
              {error}
            </div>
          )}

          {profile && (
            <pre className="rounded-lg bg-gray-50 border border-gray-100 p-4 text-xs text-gray-700 overflow-auto">
              {JSON.stringify(profile, null, 2)}
            </pre>
          )}
        </div>
      </main>
    </div>
  )
}
