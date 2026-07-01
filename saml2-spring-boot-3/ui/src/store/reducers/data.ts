import { createReducer } from '@reduxjs/toolkit';
import { APIStatus, AuthProvider, isAPIError, Nullable, User } from '~/types/common';
import { RootState } from '..';
import {
  authProvidersListLoadingCompleted,
  authProvidersSaveCompleted,
  setSelectedAuthProvider,
  setSelectedUser,
  userAuthProvidersListLoadingCompleted,
  usersListLoadingCompleted
} from '../actions';
import {
  listAuthProviders,
  listUserAuthProviders,
  listUsers,
  saveAuthProvider,
} from '../actions/thunk-actions';

interface DataStore {
  selectedUser: Nullable<User>;
  selectedAuthProvider: Nullable<AuthProvider>;
  usersList: User[];
  usersListStatus: APIStatus;
  usersListError: Nullable<string>;
  authProviders: AuthProvider[];
  authProviderListStatus: APIStatus;
  authProviderListError: Nullable<string>;
  userAuthProviders: AuthProvider[];
  userAuthProviderListStatus: APIStatus;
  userAuthProviderListError: Nullable<string>;
  authProviderSaveStatus: APIStatus;
  authProviderSaveError: Nullable<string>;
}

const initialState: DataStore = {
  selectedUser: null,
  selectedAuthProvider: null,
  usersList: [],
  usersListStatus: APIStatus.IDLE,
  usersListError: null,
  authProviders: [],
  authProviderListStatus: APIStatus.IDLE,
  authProviderListError: null,
  userAuthProviders: [],
  userAuthProviderListStatus: APIStatus.IDLE,
  userAuthProviderListError: null,
  authProviderSaveStatus: APIStatus.IDLE,
  authProviderSaveError: null,
};

const reducer = createReducer<DataStore>(initialState, (builder) => {
  builder
    .addCase(setSelectedAuthProvider, (state, action) => {
      state.selectedAuthProvider = action.payload;
    })
    .addCase(saveAuthProvider.pending, (state) => {
      state.authProviderSaveStatus = APIStatus.LOADING;
    })
    .addCase(saveAuthProvider.fulfilled, (state, action) => {
      const response = action.payload;
      if (isAPIError(response)) {
        state.authProviderSaveStatus = APIStatus.ERROR;
        state.authProviderSaveError = response.message;
      } else {
        state.authProviderSaveStatus = APIStatus.SUCCESS;
        state.selectedAuthProvider = response;
      }
    })
    .addCase(authProvidersSaveCompleted, (state) => {
      state.authProviderSaveError = null;
      state.authProviderSaveStatus = APIStatus.IDLE;
    })
    .addCase(listAuthProviders.pending, (state) => {
      state.authProviderListStatus = APIStatus.LOADING;
    })
    .addCase(listAuthProviders.fulfilled, (state, action) => {
      const response = action.payload;
      if (isAPIError(response)) {
        state.authProviderListStatus = APIStatus.ERROR;
        state.authProviderListError = response.message;
      } else {
        state.authProviderListStatus = APIStatus.SUCCESS;
        state.authProviders = response;
      }
    })
    .addCase(authProvidersListLoadingCompleted, (state) => {
      state.authProviderListError = null;
      state.authProviderListStatus = APIStatus.IDLE;
    })
    .addCase(listUserAuthProviders.pending, (state) => {
      state.userAuthProviderListStatus = APIStatus.LOADING;
    })
    .addCase(listUserAuthProviders.fulfilled, (state, action) => {
      const response = action.payload;
      if (isAPIError(response)) {
        state.userAuthProviderListStatus = APIStatus.ERROR;
        state.userAuthProviderListError = response.message;
      } else {
        state.userAuthProviderListStatus = APIStatus.SUCCESS;
        state.userAuthProviders = response;
      }
    })
    .addCase(userAuthProvidersListLoadingCompleted, (state) => {
      state.userAuthProviderListError = null;
      state.userAuthProviderListStatus = APIStatus.IDLE;
    })
    .addCase(setSelectedUser, (state, action) => {
      state.selectedUser = action.payload;
    })
    .addCase(listUsers.pending, (state) => {
      state.usersListStatus = APIStatus.LOADING;
    })
    .addCase(listUsers.fulfilled, (state, action) => {
      const response = action.payload;
      if (isAPIError(response)) {
        state.usersListStatus = APIStatus.ERROR;
        state.usersListError = response.message;
      } else {
        state.usersListStatus = APIStatus.SUCCESS;
        state.usersList = response;
      }
    })
    .addCase(usersListLoadingCompleted, (state) => {
      state.usersListError = null;
      state.usersListStatus = APIStatus.IDLE;
    });
});

const selectSelectedAuthProvider = (state: RootState) => state.dataReducer.selectedAuthProvider;
const selectSelectedUser = (state: RootState) => state.dataReducer.selectedUser;
const selectUsers = (state: RootState) => state.dataReducer.usersList;
const selectUsersLoading = (state: RootState) => state.dataReducer.usersListStatus;
const selectUsersError = (state: RootState) => state.dataReducer.usersListError;
const selectAuthProviders = (state: RootState) => state.dataReducer.authProviders;
const selectAuthProviderListStatus = (state: RootState) => state.dataReducer.authProviderListStatus;
const selectAuthProviderListError = (state: RootState) => state.dataReducer.authProviderListError;
const selectUserAuthProviders = (state: RootState) => state.dataReducer.userAuthProviders;
const selectUserAuthProviderListStatus = (state: RootState) =>
  state.dataReducer.userAuthProviderListStatus;
const selectUserAuthProviderListError = (state: RootState) =>
  state.dataReducer.userAuthProviderListError;
const selectAuthProviderSaveStatus = (state: RootState) => state.dataReducer.authProviderSaveStatus;
const selectAuthProviderSaveError = (state: RootState) => state.dataReducer.authProviderSaveError;

export {
  reducer,
  selectAuthProviderListError,
  selectAuthProviderListStatus,
  selectAuthProviders, selectAuthProviderSaveError,
  selectAuthProviderSaveStatus,
  selectSelectedAuthProvider,
  selectSelectedUser, selectUserAuthProviderListError, selectUserAuthProviderListStatus, selectUserAuthProviders, selectUsers,
  selectUsersError,
  selectUsersLoading
};

