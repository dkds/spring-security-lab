import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <div className="text-center">
        <p className="text-6xl font-bold text-indigo-200">404</p>
        <h1 className="mt-4 text-xl font-semibold text-gray-800">Page not found</h1>
        <p className="mt-2 text-sm text-gray-500">The page you're looking for doesn't exist.</p>
        <Link
          to="/dashboard"
          className="mt-6 inline-block rounded-lg bg-indigo-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-indigo-700 transition-colors"
        >
          Go to dashboard
        </Link>
      </div>
    </div>
  )
}
