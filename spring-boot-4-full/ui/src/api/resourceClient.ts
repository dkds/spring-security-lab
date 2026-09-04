/**
 * HTTP client for the resource-server.
 * Automatically attaches the Bearer token from the auth context.
 */

const RESOURCE_BASE = import.meta.env.VITE_RESOURCE_BASE_URL ?? 'http://localhost:9001'

interface FetchOptions extends RequestInit {
  token: string
}

async function apiFetch<T>(path: string, { token, ...init }: FetchOptions): Promise<T> {
  const response = await fetch(`${RESOURCE_BASE}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      ...init.headers,
    },
  })

  if (!response.ok) {
    const text = await response.text()
    throw new Error(`API ${response.status}: ${text}`)
  }

  // 204 No Content
  if (response.status === 204) return undefined as T

  return response.json() as Promise<T>
}

/** Fetch the current user's profile from the resource server. */
export async function fetchProfile(token: string): Promise<Record<string, unknown>> {
  return apiFetch<Record<string, unknown>>('/api/profile', { token })
}

export interface Task {
  id: number
  title: string
  done: boolean
}

/** Fetch the dummy task list from the resource server. */
export async function fetchTasks(token: string): Promise<Task[]> {
  return apiFetch<Task[]>('/api/tasks', { token })
}

/** Generic GET helper — extend with more endpoints as the resource-server grows. */
export async function fetchResource<T>(path: string, token: string): Promise<T> {
  return apiFetch<T>(path, { token })
}
