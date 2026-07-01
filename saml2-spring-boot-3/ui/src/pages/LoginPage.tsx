import React, { useEffect } from 'react';
import { SubmitHandler, useForm } from 'react-hook-form';
import { useNavigate } from 'react-router';
import { toast, ToastContainer } from 'react-toastify';
import ToastMessage from '~/components/ToastMessage';
import { ROUTES } from '~/resources/routes-constants';
import { useAppDispatch, useAppSelector } from '~/store';
import { loginCompleted } from '~/store/actions';
import { login } from '~/store/actions/thunk-actions';
import { selectLoggedInUser, selectLoginError, selectLoginProcessing } from '~/store/reducers/user';
import { useNoLoggedInUser } from '~/utility/functions';

type Inputs = {
  email: string;
  password: string;
};

const LoginPage: React.FC = () => {
  useNoLoggedInUser();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const loginProcessing = useAppSelector(selectLoginProcessing);
  const loggedInUser = useAppSelector(selectLoggedInUser);
  const loginError = useAppSelector(selectLoginError);
  const { register, handleSubmit } = useForm<Inputs>();

  const onSubmit: SubmitHandler<Inputs> = (data) => {
    dispatch(login({ ...data }));
  };

  useEffect(() => {
    if (loggedInUser) {
      navigate(ROUTES.HOMEPAGE);
      dispatch(loginCompleted());
    }
  }, [loggedInUser]);

  useEffect(() => {
    if (loginError) {
      toast.error(<ToastMessage title="Error" message={loginError} />);
    }
  }, [loginError]);

  const handleSSOLoginClick = (): void => {
    navigate(ROUTES.SSO_LOGIN_INIT);
  };
  const handleRegisterClick = (): void => {
    navigate(ROUTES.USER_REGISTER);
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
                  className="block w-full rounded-md bg-white/5 px-3 py-1.5 text-base text-white outline-1 -outline-offset-1 outline-white/10 placeholder:text-gray-500 focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-500 sm:text-sm/6"
                  {...register('email', { required: true, maxLength: 20 })}
                  disabled={loginProcessing}
                />
              </div>
            </div>

            <div>
              <div className="flex items-center justify-between">
                <label htmlFor="password" className="block text-sm/6 font-medium text-gray-100">
                  Password
                </label>
              </div>
              <div className="mt-2">
                <input
                  id="password"
                  type="password"
                  autoComplete="current-password"
                  className="block w-full rounded-md bg-white/5 px-3 py-1.5 text-base text-white outline-1 -outline-offset-1 outline-white/10 placeholder:text-gray-500 focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-500 sm:text-sm/6"
                  {...register('password', { required: true, maxLength: 25 })}
                  disabled={loginProcessing}
                />
              </div>
            </div>

            <div>
              <button
                type="submit"
                className="flex w-full justify-center rounded-md bg-indigo-500 px-3 py-1.5 text-sm/6 font-semibold text-white hover:bg-indigo-400 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500"
                disabled={loginProcessing}
              >
                Sign in
              </button>
            </div>
          </form>

          <div className="mt-12 mb-2 flex justify-center space-x-4">
            <button
              type="button"
              className="flex w-full justify-center rounded-md bg-indigo-500 px-3 py-1.5 text-sm/6 font-semibold text-white hover:bg-indigo-400 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500"
              onClick={handleSSOLoginClick}
              disabled={loginProcessing}
            >
              Login with SSO
            </button>
          </div>

          <div className="mt-6 mb-2 flex justify-center space-x-4">
            <button
              type="button"
              className="flex w-full justify-center rounded-md bg-indigo-500 px-3 py-1.5 text-sm/6 font-semibold text-white hover:bg-indigo-400 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500"
              onClick={handleRegisterClick}
              disabled={loginProcessing}
            >
              Register
            </button>
          </div>
        </div>
      </div>
      <ToastContainer />
    </>
  );
};

export default LoginPage;
