import { useEffect } from 'react';
import { useStore } from 'react-redux';
import { useNavigate } from 'react-router';
import { ROUTES } from '~/resources/routes-constants';
import { RootState } from '~/store';
import { APIError } from '~/types/common';

export const useNoLoggedInUser = () => {
  const navigate = useNavigate();
  const store = useStore();

  useEffect(() => {
    if ((store.getState() as RootState).userReducer.loggedInUser) {
      console.log('useNoLoggedInUser');
      navigate(ROUTES.HOMEPAGE);
    }
  }, []);
};
export const useLoggedInUser = () => {
  const navigate = useNavigate();
  const store = useStore();

  useEffect(() => {
    if (!(store.getState() as RootState).userReducer.loggedInUser) {
      console.log('useLoggedInUser');
      navigate(ROUTES.USER_LOGIN);
    }
  }, []);
};
export const handleAPIError = (error: any): APIError => {
  return {
    error: true,
    status: error.status,
    message: error.response?.data?.message || error.message,
  };
};
