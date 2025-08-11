import React, { useEffect } from 'react';
import { SubmitHandler, useForm } from 'react-hook-form';
import { NavLink, useNavigate, useSearchParams } from 'react-router';
import { toast, ToastContainer } from 'react-toastify';
import ToastMessage from '~/components/ToastMessage';
import { ROUTES } from '~/resources/routes-constants';
import { useAppDispatch, useAppSelector } from '~/store';
import { setSSOUsername, userAuthProvidersListLoadingCompleted } from '~/store/actions';
import { listUserAuthProviders, logoutUser } from '~/store/actions/thunk-actions';
import {
  selectUserAuthProviderListError,
  selectUserAuthProviderListStatus,
  selectUserAuthProviders,
} from '~/store/reducers/data';
import { APIStatus } from '~/types/common';

type Inputs = {
  email: string;
};

const SSOLoginInitPage: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const [searchParams] = useSearchParams();
  const userAuthProvidersError = useAppSelector(selectUserAuthProviderListError);
  const userAuthProvidersStatus = useAppSelector(selectUserAuthProviderListStatus);
  const userAuthProviders = useAppSelector(selectUserAuthProviders);
  const { register, handleSubmit } = useForm<Inputs>();

  const userAuthProvidersLoading = userAuthProvidersStatus === APIStatus.LOADING;

  useEffect(() => {
    dispatch(setSSOUsername(null));
  }, []);

  useEffect(() => {
    const error = searchParams.get('error');
    console.log('error', error);
    if (error) {
      dispatch(logoutUser({ isSSO: true }));
      toast.error(<ToastMessage title="Error" message={error} />);
    }
  }, []);

  useEffect(() => {
    if (userAuthProvidersStatus === APIStatus.SUCCESS) {
      if (userAuthProviders?.length) {
        navigate(ROUTES.SSO_LOGIN_SELECT);
        dispatch(userAuthProvidersListLoadingCompleted());
      } else {
        toast.error(
          <ToastMessage
            title="Error"
            message="No providers found. Please contact your administrator."
          />
        );
      }
    }
  }, [userAuthProviders]);

  useEffect(() => {
    if (userAuthProvidersError) {
      toast.error(<ToastMessage title="Error" message={userAuthProvidersError} />);
    }
  }, [userAuthProvidersError]);

  const onSubmit: SubmitHandler<Inputs> = (data) => {
    dispatch(setSSOUsername(data.email));
    dispatch(listUserAuthProviders(data.email));
  };

  return (
    <>
      <div className="flex min-h-full flex-col justify-center px-6 py-12 lg:px-8">
        <div className="sm:mx-auto sm:w-full sm:max-w-sm">
          <img
            alt="Your Company"
            src="https://tailwindcss.com/plus-assets/img/logos/mark.svg?color=indigo&shade=500"
            className="mx-auto h-10 w-auto"
          />
          <h2 className="mt-10 text-center text-2xl/9 font-bold tracking-tight text-white">
            Sign in to your account
          </h2>
        </div>

        <div className="mt-10 sm:mx-auto sm:w-full sm:max-w-sm">
          <form action="#" method="POST" className="space-y-6" onSubmit={handleSubmit(onSubmit)}>
            <div>
              <label htmlFor="email" className="block text-sm/6 font-medium text-gray-100">
                Email address
              </label>
              <div className="mt-2">
                <input
                  id="email"
                  type="email"
                  autoComplete="email"
                  disabled={userAuthProvidersLoading}
                  className="block w-full rounded-md bg-white/5 px-3 py-1.5 text-base text-white outline-1 -outline-offset-1 outline-white/10 placeholder:text-gray-500 focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-500 sm:text-sm/6"
                  {...register('email', { required: true, maxLength: 50 })}
                />
              </div>
            </div>

            <div>
              <button
                type="submit"
                disabled={userAuthProvidersLoading}
                className="flex w-full justify-center rounded-md bg-indigo-500 px-3 py-1.5 text-sm/6 font-semibold text-white hover:bg-indigo-400 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500"
              >
                Get Auth Providers
              </button>
            </div>
            <div className="float-right">
              <NavLink to="/login" className="text-blue-300">
                Back
              </NavLink>
            </div>
          </form>
        </div>
      </div>
      <ToastContainer />
    </>
  );
};

export default SSOLoginInitPage;
