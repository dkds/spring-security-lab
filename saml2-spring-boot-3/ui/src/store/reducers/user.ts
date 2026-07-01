import { createReducer } from '@reduxjs/toolkit';
import { APIStatus, isAPIError, Nullable, SSOResponse, User } from '~/types/common';
import { RootState } from '..';
import { loginCompleted, registrationCompleted, setSSOUsername, ssoInitReset } from '../actions';
import {
  exchangeAuthCode,
  initiateSSO,
  login,
  logoutUser,
  registerUser,
} from '../actions/thunk-actions';

interface UserState {
  loggedInUser: User | null;
  loginError: string | null;
  loginStatus: APIStatus;
  registeredUser: User | null;
  registrationError: string | null;
  registrationStatus: APIStatus;
  selectedSSOUsername: Nullable<string>;
  ssoDetails: SSOResponse | null;
  ssoInitError: string | null;
  ssoInitStatus: APIStatus;
}

const initialState: UserState = {
  loggedInUser: null,
  loginError: null,
  loginStatus: APIStatus.IDLE,
  registeredUser: null,
  registrationError: null,
  registrationStatus: APIStatus.IDLE,
  selectedSSOUsername: null,
  ssoDetails: null,
  ssoInitError: null,
  ssoInitStatus: APIStatus.IDLE,
};

const reducer = createReducer<UserState>(initialState, (builder) => {
  builder
    .addCase(exchangeAuthCode.pending, (state) => {
      state.loggedInUser = null;
      state.loginError = null;
      state.loginStatus = APIStatus.LOADING;
    })
    .addCase(exchangeAuthCode.fulfilled, (state, action) => {
      const response = action.payload;
      if (isAPIError(response)) {
        state.loginStatus = APIStatus.ERROR;
        state.loginError = response.message;
      } else {
        state.loginStatus = APIStatus.SUCCESS;
        state.loggedInUser = response;
      }
    })
    .addCase(initiateSSO.pending, (state) => {
      state.ssoDetails = null;
      state.ssoInitError = null;
      state.ssoInitStatus = APIStatus.LOADING;
    })
    .addCase(initiateSSO.fulfilled, (state, action) => {
      const response = action.payload;
      if (isAPIError(response)) {
        state.ssoInitStatus = APIStatus.ERROR;
        state.ssoInitError = response.message;
      } else {
        state.ssoInitStatus = APIStatus.SUCCESS;
        state.ssoDetails = response;
      }
    })
    .addCase(ssoInitReset, (state) => {
      state.ssoDetails = null;
      state.ssoInitError = null;
      state.ssoInitStatus = APIStatus.IDLE;
    })
    .addCase(setSSOUsername, (state, action) => {
      state.selectedSSOUsername = action.payload;
    })
    .addCase(registerUser.pending, (state) => {
      state.registeredUser = null;
      state.registrationError = null;
      state.registrationStatus = APIStatus.LOADING;
    })
    .addCase(registerUser.fulfilled, (state, action) => {
      const response = action.payload;
      if (isAPIError(response)) {
        state.registrationStatus = APIStatus.ERROR;
        state.registrationError = response.message;
      } else {
        state.registrationStatus = APIStatus.SUCCESS;
        state.registeredUser = response;
      }
    })
    .addCase(registrationCompleted, (state) => {
      state.registrationError = null;
      state.registrationStatus = APIStatus.IDLE;
    })
    .addCase(login.pending, (state) => {
      state.loggedInUser = null;
      state.loginError = null;
      state.loginStatus = APIStatus.LOADING;
    })
    .addCase(login.fulfilled, (state, action) => {
      const response = action.payload;
      if (isAPIError(response)) {
        state.loginStatus = APIStatus.ERROR;
        state.loginError = response.message;
      } else {
        state.loginStatus = APIStatus.SUCCESS;
        state.loggedInUser = response;
      }
    })
    .addCase(loginCompleted, (state) => {
      state.loginError = null;
      state.loginStatus = APIStatus.IDLE;
    })
    .addCase(logoutUser.fulfilled, (state) => {
      state.loggedInUser = null;
      state.loginError = null;
      state.loginStatus = APIStatus.IDLE;
    });
});

const selectSSODetails = (state: RootState) => state.userReducer.ssoDetails;
const selectSSOInitSuccess = (state: RootState) =>
  state.userReducer.ssoInitStatus === APIStatus.SUCCESS;
const selectLoggedInUser = (state: RootState) => state.userReducer.loggedInUser;
const selectLoginProcessing = (state: RootState) =>
  state.userReducer.loginStatus === APIStatus.LOADING;
const selectLoginError = (state: RootState) => state.userReducer.loginError;
const selectRegisteredUser = (state: RootState) => state.userReducer.registeredUser;
const selectRegistrationProcessing = (state: RootState) =>
  state.userReducer.registrationStatus === APIStatus.LOADING;
const selectRegistrationError = (state: RootState) => state.userReducer.registrationError;
const selectSSOUsername = (state: RootState) => state.userReducer.selectedSSOUsername;

export {
  reducer,
  selectLoggedInUser,
  selectLoginError,
  selectLoginProcessing,
  selectRegisteredUser,
  selectRegistrationError,
  selectRegistrationProcessing,
  selectSSODetails,
  selectSSOInitSuccess,
  selectSSOUsername,
};
