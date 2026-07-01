import { createAction } from '@reduxjs/toolkit';
import { User } from '~/types/common';

export const setSSOUsername = createAction<string | null>('data/setSSOUsername');
export const setSelectedUser = createAction<User | null>('data/setSelectedUser');
export const setSelectedAuthProvider = createAction<any>('data/setSelectedAuthProvider');
export const usersListLoadingCompleted = createAction<void>('data/usersListLoadingCompleted');
export const authProvidersListLoadingCompleted = createAction<void>(
  'data/authProvidersListLoadingCompleted'
);
export const userAuthProvidersListLoadingCompleted = createAction<void>(
  'data/userAuthProvidersListLoadingCompleted'
);
export const authProvidersSaveCompleted = createAction<void>('data/authProvidersSaveCompleted');
export const registrationCompleted = createAction<void>('user/registrationCompleted');
export const loginCompleted = createAction<void>('user/loginCompleted');
export const ssoInitReset = createAction<void>('user/ssoInitReset');
export const logout = createAction<void>('user/logout');
