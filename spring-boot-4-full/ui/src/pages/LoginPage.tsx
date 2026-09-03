import { useEffect } from 'react';
import { useAuth } from 'react-oidc-context';

/**
 * Login page - immediately initiates the OAuth2 redirect to auth-server.
 * The user should never see this page for more than a brief moment before
 * being redirected to the auth-server's login screen.
 */
function LoginPage() {
  const auth = useAuth();

  useEffect(() => {
    if (!auth.isLoading && !auth.isAuthenticated) {
      auth.signinRedirect();
    }
  }, [auth.isLoading, auth.isAuthenticated]);

  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh' }}>
      <p>Redirecting to login...</p>
    </div>
  );
}

export default LoginPage;
