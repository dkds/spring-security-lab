export const oidcConfig = {
  authority: "http://localhost:9000",
  client_id: "react-spa",
  redirect_uri: "http://localhost:3000/callback",
  post_logout_redirect_uri: "http://localhost:3000",
  scope: "openid profile api.read offline_access",
  response_type: "code",
  automaticSilentRenew: true, // automatically refresh before expiry
};
