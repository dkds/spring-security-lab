import { useEffect } from "react";
import { useAuth } from "react-oidc-context";
import { useNavigate } from "react-router";

export default function Callback() {
  const auth = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (!auth.isLoading && auth.isAuthenticated) {
      navigate("/dashboard");
    }
  }, [auth.isLoading, auth.isAuthenticated]);

  return <div>Completing sign in...</div>;
}
