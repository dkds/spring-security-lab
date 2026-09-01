import { useState } from "react";
import { useAuth } from "react-oidc-context";

export default function Dashboard() {
  const auth = useAuth();
  const [apiResponse, setApiResponse] = useState(null);

  if (!auth.isAuthenticated) {
    return (
      <div>
        Not authenticated. <a href="/">Go home</a>
      </div>
    );
  }

  const callApi = async () => {
    const response = await fetch("http://localhost:8080/api/hello", {
      headers: {
        Authorization: `Bearer ${auth.user?.access_token}`,
      },
    });
    const data = await response.json();
    setApiResponse(data);
  };

  const handleLogout = () => {
    auth.signoutRedirect();
  };

  return (
    <div>
      <h1>Dashboard</h1>
      <p>Welcome, {auth.user?.profile?.sub}</p>
      <p>
        Token expires at:{" "}
        {new Date(auth.user?.expires_at || 1 * 1000).toString()} 
        ({auth.user?.expires_at} millis)
      </p>
      <p>Token expired: {auth.user?.expired ? "Yes" : "No"}</p>
      <button onClick={callApi}>Call Protected API</button>
      <button onClick={handleLogout}>Sign Out</button>
      {apiResponse && <pre>{JSON.stringify(apiResponse, null, 2)}</pre>}
    </div>
  );
}
