import { useAppAuth } from '../auth/useAuthHook';

/**
 * Dashboard page - authenticated user view.
 * Shows user info and logout button.
 */
function DashboardPage() {
  const auth = useAppAuth();

  return (
    <div className="dashboard-container">
      <div className="dashboard-header">
        <h1>Welcome, {auth.user?.profile?.name || auth.user?.profile?.preferred_username}!</h1>
        <button 
          className="logout-button"
          onClick={() => auth.logout()}
        >
          Logout
        </button>
      </div>

      <div className="user-info">
        <h2>User Information</h2>
        <table>
          <tbody>
            <tr>
              <td><strong>Username:</strong></td>
              <td>{auth.user?.profile?.preferred_username}</td>
            </tr>
            <tr>
              <td><strong>Email:</strong></td>
              <td>{auth.user?.profile?.email}</td>
            </tr>
            <tr>
              <td><strong>Name:</strong></td>
              <td>{auth.user?.profile?.name}</td>
            </tr>
            <tr>
              <td><strong>Token Expires At:</strong></td>
              <td>{new Date(auth.user?.expires_at ? auth.user.expires_at * 1000 : 0).toLocaleString()}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div className="token-info">
        <h2>Access Token</h2>
        <code className="token-display">
          {auth.accessToken?.substring(0, 50)}...
        </code>
      </div>

      <style>{`
        .dashboard-container {
          max-width: 800px;
          margin: 0 auto;
          padding: 20px;
        }

        .dashboard-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 30px;
        }

        .logout-button {
          padding: 10px 20px;
          background: #c33;
          color: white;
          border: none;
          border-radius: 4px;
          cursor: pointer;
          font-weight: 600;
        }

        .logout-button:hover {
          background: #a22;
        }

        .user-info, .token-info {
          background: #f5f5f5;
          padding: 20px;
          border-radius: 8px;
          margin-bottom: 20px;
        }

        .user-info table {
          width: 100%;
          border-collapse: collapse;
        }

        .user-info td {
          padding: 12px;
          border-bottom: 1px solid #ddd;
        }

        .token-display {
          display: block;
          background: white;
          padding: 15px;
          border-radius: 4px;
          overflow-x: auto;
          font-size: 12px;
          word-break: break-all;
        }
      `}</style>
    </div>
  );
}

export default DashboardPage;
