// export const baseUrl = 'http://localhost:8090';
// const apiBase = '/api';

// export const LIST_AUTH_PROVIDERS = baseUrl + apiBase + '/auth-providers';
// export const ADD_AUTH_PROVIDER = baseUrl + apiBase + '/auth-providers';
// export const LIST_USER_AUTH_PROVIDERS = baseUrl + apiBase + '/users/auth-providers';
// export const ADD_AUTH_PROVIDER_TO_USER = baseUrl + apiBase + '/users/auth-providers';
// export const USER_REGISTER = baseUrl + apiBase + '/users/register';
// export const LIST_USERS = baseUrl + apiBase + '/users';
// export const SSO_INITIATE = baseUrl + '/sso/initiate';
// export const USER_LOGIN = baseUrl + '/oauth2/token';
// export const USER_LOGOUT = baseUrl + '/logout';

export const baseUrl = 'http://localhost:8080';
const apiBase = '/as-api';

export const LIST_AUTH_PROVIDERS = apiBase + '/auth-providers';
export const ADD_AUTH_PROVIDER = apiBase + '/auth-providers';
export const ADD_AUTH_PROVIDER_TO_USER = apiBase + '/users/auth-providers';
export const USER_REGISTER = apiBase + '/users/register';
export const LIST_USERS = apiBase + '/users';
export const LIST_USER_AUTH_PROVIDERS = apiBase + '/sso/redirect-paths';
export const SSO_INITIATE = apiBase + '/sso/initiate';
export const USER_LOGIN = '/oauth/token';
export const USER_LOGOUT = apiBase + '/logout';
export const SSO_LOGOUT = '/sso/logout';
