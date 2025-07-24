'use client';

import { getUserManager } from '@/util/oidc.client';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';

const CallbackPage = () => {
  const router = useRouter();

  useEffect(() => {
    const handleCallback = async () => {
      try {
        const userManager = getUserManager();
        await userManager.signinRedirectCallback(); // processes token response
        router.replace('/home'); // redirect to homepage after login
      } catch (e) {
        console.error('Callback error:', e);
      }
    };

    handleCallback();
  }, [router]);

  return <div>Signing in...</div>;
};

export default CallbackPage;
