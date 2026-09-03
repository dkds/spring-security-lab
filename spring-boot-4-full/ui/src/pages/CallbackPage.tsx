import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';

/**
 * Callback page - handles OAuth2 authorization code redirect.
 * react-oidc-context automatically handles the code exchange when it detects
 * the authorization code in the URL. We wait for it to complete and redirect.
 */
function CallbackPage() {
  const auth = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (auth.isAuthenticated) {
      // Tokens received - navigate to dashboard without a full page reload
      navigate('/dashboard', { replace: true });
    }
  }, [auth.isAuthenticated, navigate]);

  if (auth.error) {
    return (
      <div style={{ padding: '2rem', textAlign: 'center' }}>
        <h1>Authentication Error</h1>
        <p>{auth.error.message}</p>
        <button onClick={() => auth.signinRedirect()}>Try Again</button>
      </div>
    );
  }

  return (
    <div style={{ padding: '2rem', textAlign: 'center' }}>
      <h1>Completing authentication...</h1>
      <p>Please wait while we exchange your authorization code for tokens.</p>
    </div>
  );
}

export default CallbackPage;
