import { AuthProvider } from 'react-oidc-context';
import { oidcConfig } from './auth/oidcConfig';
import AppRoutes from './AppRoutes';

/**
 * Root App component.
 * Wraps the entire app with OIDC authentication provider.
 */
function App() {
  return (
    <AuthProvider {...oidcConfig}>
      <AppRoutes />
    </AuthProvider>
  );
}

export default App;
