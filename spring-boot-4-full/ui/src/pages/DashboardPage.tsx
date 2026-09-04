import { useEffect, useState } from 'react';
import { useAppAuth } from '../auth/useAuthHook';
import { fetchTasks, type Task } from '../api/resourceClient';

/**
 * Dashboard page - authenticated user view.
 * Shows user info, logout button, and a task list fetched from the
 * resource-server's /api/tasks endpoint (secured by the access token).
 */
function DashboardPage() {
  const auth = useAppAuth();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [tasksError, setTasksError] = useState<string | null>(null);
  const [tasksLoading, setTasksLoading] = useState(true);

  useEffect(() => {
    if (!auth.accessToken) return;

    fetchTasks(auth.accessToken)
      .then(setTasks)
      .catch((err: Error) => setTasksError(err.message))
      .finally(() => setTasksLoading(false));
  }, [auth.accessToken]);

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

      <div className="tasks-info">
        <h2>Tasks (from resource-server)</h2>
        {tasksLoading && <p>Loading tasks...</p>}
        {tasksError && <p className="tasks-error">Failed to load tasks: {tasksError}</p>}
        {!tasksLoading && !tasksError && (
          <ul className="tasks-list">
            {tasks.map((task) => (
              <li key={task.id} className={task.done ? 'task-done' : ''}>
                <input type="checkbox" checked={task.done} readOnly />
                <span>{task.title}</span>
              </li>
            ))}
          </ul>
        )}
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

        .user-info, .token-info, .tasks-info {
          background: #f5f5f5;
          padding: 20px;
          border-radius: 8px;
          margin-bottom: 20px;
        }

        .tasks-error {
          color: #c33;
        }

        .tasks-list {
          list-style: none;
          padding: 0;
          margin: 0;
        }

        .tasks-list li {
          display: flex;
          align-items: center;
          gap: 10px;
          padding: 10px 0;
          border-bottom: 1px solid #ddd;
        }

        .tasks-list li:last-child {
          border-bottom: none;
        }

        .tasks-list li.task-done span {
          text-decoration: line-through;
          color: #888;
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
