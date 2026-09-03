import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { useAppAuth } from './auth/useAuthHook';
import RequireAuth from './auth/RequireAuth';
import LoginPage from './pages/LoginPage';
import CallbackPage from './pages/CallbackPage';
import DashboardPage from './pages/DashboardPage';
import NotFoundPage from './pages/NotFoundPage';

/**
 * Application routes with authentication guards.
 */
function AppRoutes() {
  const auth = useAppAuth();

  if (auth.isLoading) {
    return <div className="loading">Initializing authentication...</div>;
  }

  return (
    <Router>
      <Routes>
        {/* Public routes */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/callback" element={<CallbackPage />} />

        {/* Protected routes */}
        <Route
          path="/dashboard"
          element={
            <RequireAuth>
              <DashboardPage />
            </RequireAuth>
          }
        />

        {/* Default redirect */}
        <Route path="/" element={<Navigate to="/dashboard" replace />} />

        {/* 404 */}
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Router>
  );
}

export default AppRoutes;
