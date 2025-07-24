import { UserManagerSettings } from 'oidc-client-ts';

export const oidcConfig: UserManagerSettings = {
    authority: 'http://localhost:8095',
    client_id: 'web-ui',
    redirect_uri: 'http://localhost:3001/callback',
    post_logout_redirect_uri: 'http://localhost:3001/',
    response_type: 'code',
    scope: 'openid read',
};
