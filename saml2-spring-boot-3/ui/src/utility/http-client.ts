import { Store } from '@reduxjs/toolkit';
import axios, { AxiosError, AxiosRequestConfig } from 'axios';
import { USER_LOGIN } from '~/resources/api-constants';

const instance = axios.create();

const setupInterceptor = (store: Store) => {
  const handleError = async (error: AxiosError) => {
    return Promise.reject(error);
  };

  instance.interceptors.request.use(async (config: any | AxiosRequestConfig) => {
    if (config.url === USER_LOGIN) {
      return config;
    }
    const token = store.getState().userReducer.loggedInUser?.token;
    console.log(store.getState().userReducer.loggedInUser);

    if (token) {
      config.headers.Authorization = 'Bearer ' + token;
    }
    return config;
  });

  instance.interceptors.response.use((response) => response, handleError);
};

export { instance as axios, setupInterceptor };
