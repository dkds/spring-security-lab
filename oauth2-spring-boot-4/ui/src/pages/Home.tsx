import { useAuth } from "react-oidc-context";

export default function Home() {
  const auth = useAuth();

  if (auth.isLoading) return <div>Loading...</div>;
  if (auth.isAuthenticated) window.location.href = "/dashboard";
  return (
    <div>
      <h1>POC App</h1>
      <button onClick={() => auth.signinRedirect()}>Sign In</button>
    </div>
  );
}
