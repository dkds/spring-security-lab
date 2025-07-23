'use client';

import { useEffect } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';

export default function CallbackPage() {
  const router = useRouter();
  const searchParams = useSearchParams();

  useEffect(() => {
    const exchangeAuthorizationCode = async () => {
      const code = searchParams.get('code');
      const state = searchParams.get('state');

      if (!code) {
        console.error("Authorization code not found");
        return;
      }

      const codeVerifier = localStorage.getItem('pkce_code_verifier');
      if (!codeVerifier) {
        console.error("Missing PKCE code_verifier");
        return;
      }

      try {
        const response = await fetch('http://localhost:8095/oauth2/token', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
          },
          body: new URLSearchParams({
            grant_type: 'authorization_code',
            code: code,
            redirect_uri: 'http://localhost:3001/callback',
            client_id: 'web-ui',
            code_verifier: codeVerifier,
          }),
        });

        if (!response.ok) {
          console.error("Token exchange failed", await response.text());
          return;
        }

        const tokenData = await response.json();
        console.log("Access token:", tokenData);

        localStorage.setItem('access_token', tokenData.access_token);

        // ✅ App Router: use replace or push
        router.replace('/');
      } catch (error) {
        console.error("Token exchange error", error);
      }
    };

    exchangeAuthorizationCode();
  }, [searchParams, router]);

  return <div>Processing login...</div>;
}
