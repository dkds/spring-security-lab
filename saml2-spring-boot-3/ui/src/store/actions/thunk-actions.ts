import { createAsyncThunk } from '@reduxjs/toolkit';
import { jwtDecode } from 'jwt-decode';
import {
  ADD_AUTH_PROVIDER,
  ADD_AUTH_PROVIDER_TO_USER,
  LIST_AUTH_PROVIDERS,
  LIST_USER_AUTH_PROVIDERS,
  LIST_USERS,
  SSO_INITIATE,
  SSO_LOGOUT,
  USER_LOGIN,
  USER_LOGOUT,
  USER_REGISTER
} from '~/resources/api-constants';
import {
  APIError,
  AuthCodeExchangeRequest,
  AuthProvider,
  AuthProviderManageRequest,
  SSORequest,
  SSOResponse,
  User,
  UserAuthProvider,
  UserLogin,
  UserLogoutRequest,
  UserRegistration,
} from '~/types/common';
import { handleAPIError } from '~/utility/functions';
import { axios } from '~/utility/http-client';

export const listAuthProviders = createAsyncThunk<AuthProvider[] | APIError>(
  'data/loadAuthProviders',
  async () => {
    return axios
      .get(LIST_AUTH_PROVIDERS)
      .then((response) => response.data as AuthProvider[])
      .catch(handleAPIError);
  }
);

export const listUserAuthProviders = createAsyncThunk<AuthProvider[] | APIError, string>(
  'data/loadUserAuthProviders',
  async (email) => {
    return axios
      .post(LIST_USER_AUTH_PROVIDERS, { email })
      .then((response) => {
        return response.data.content as AuthProvider[];
      })
      .catch(handleAPIError);
  }
);

export const addAuthProvidersToUser = createAsyncThunk<AuthProvider[] | APIError, UserAuthProvider>(
  'data/addAuthProvidersToUser',
  async (userAuthProvider) => {
    return axios
      .put(ADD_AUTH_PROVIDER_TO_USER, userAuthProvider)
      .then((response) => response.data as AuthProvider[])
      .catch(handleAPIError);
  }
);

export const saveAuthProvider = createAsyncThunk<
  AuthProvider | APIError,
  AuthProviderManageRequest
>('data/addAuthProvider', async (authProvider) => {
  return axios
    .post(ADD_AUTH_PROVIDER, authProvider)
    .then((response) => response.data as AuthProvider)
    .catch(handleAPIError);
});

export const listUsers = createAsyncThunk<User[] | APIError>('data/loadUsers', async () => {
  return axios
    .get(LIST_USERS)
    .then((response) => response.data as User[])
    .catch(handleAPIError);
});

export const exchangeAuthCode = createAsyncThunk<User | APIError, AuthCodeExchangeRequest>(
  'user/exchangeAuthCode',
  async (authCodeRequest) => {
    const clientId = 'oauth2-client';
    const clientSecret = 'test-secret';
    return axios
      .postForm(
        USER_LOGIN,
        {
          grant_type: 'code_exchange',
          username: authCodeRequest.username,
          code: authCodeRequest.code,
        },
        {
          auth: {
            username: clientId,
            password: clientSecret,
          },
        }
      )
      .then((response) => {
        const tokenResponse = response.data;
        const accessToken = tokenResponse.access_token;
        const decoded = jwtDecode(accessToken) as any;
        const user = {
          email: decoded.sub,
          role: decoded.role,
          token: accessToken,
        } as User;
        console.log(user);
        return user;
      })
      .catch(handleAPIError);
  }
);

export const initiateSSO = createAsyncThunk<SSOResponse | APIError, SSORequest>(
  'user/initiateSSO',
  async (authProvider) => {
    return axios
      .post(SSO_INITIATE, { ...authProvider })
      .then((response) => response.data as SSOResponse)
      .catch(handleAPIError);
  }
);

export const login = createAsyncThunk<User | APIError, UserLogin>(
  'user/login',
  async (userLogin) => {
    const clientId = 'oauth2-client';
    const clientSecret = 'test-secret';
    const data = {
      grant_type: 'password',
      username: userLogin.email,
      password: userLogin.password,
    };
    return axios
      .postForm(USER_LOGIN, data, {
        auth: {
          username: clientId,
          password: clientSecret,
        },
      })
      .then((response) => {
        const tokenResponse = response.data;
        const accessToken = tokenResponse.access_token;
        const decoded = jwtDecode(accessToken) as any;
        const user = {
          email: decoded.sub,
          role: decoded.role,
          token: accessToken,
        } as User;
        console.log(user);
        return user;
      })
      .catch(handleAPIError);
  }
);

export const registerUser = createAsyncThunk<User | APIError, UserRegistration>(
  'user/registerUser',
  async (userRegistration) => {
    return axios
      .post(USER_REGISTER, {
        ...userRegistration,
      })
      .then((response) => response.data as User)
      .catch(handleAPIError);
  }
);

export const logoutUser = createAsyncThunk<any | APIError, UserLogoutRequest>(
  'user/logoutUser',
  async (request) => {
    if (request.isSSO) {
      window.location.href = SSO_LOGOUT;
      return;

      // console.log(Cookies.get());
      // const csrf = Cookies.get('XSRF-TOKEN');

      // return axios
      //   .post(SSO_LOGOUT, {}, { headers: { 'X-XSRF-TOKEN': csrf } })
      //   .then((response) => {
      //     console.log(response.data);
      //     return response.data;
      //   })
      //   .catch(handleAPIError);
    }
    return axios
      .post(USER_LOGOUT)
      .then((response) => {
        // console.log(response.data);
        return response.data;
      })
      .catch(handleAPIError);
  }
);
