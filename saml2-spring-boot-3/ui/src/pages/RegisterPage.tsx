import React, { useEffect } from 'react';
import { SubmitHandler, useForm } from 'react-hook-form';
import { NavLink, useNavigate } from 'react-router';
import { toast, ToastContainer } from 'react-toastify';
import ToastMessage from '~/components/ToastMessage';
import { ROUTES } from '~/resources/routes-constants';
import { useAppDispatch, useAppSelector } from '~/store';
import { registrationCompleted } from '~/store/actions';
import { registerUser } from '~/store/actions/thunk-actions';
import { selectRegisteredUser, selectRegistrationError, selectRegistrationProcessing } from '~/store/reducers/user';

type Inputs = {
  email: string;
  password: string;
  confirmPassword: string;
};

const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const registrationProcessing = useAppSelector(selectRegistrationProcessing);
  const registrationError = useAppSelector(selectRegistrationError);
  const registeredUser = useAppSelector(selectRegisteredUser);
  const { register, handleSubmit } = useForm<Inputs>();

  const onSubmit: SubmitHandler<Inputs> = (data) => {
    dispatch(registerUser(data));
  };

  useEffect(() => {
    if (registeredUser) {
      navigate(ROUTES.USER_LOGIN);
      dispatch(registrationCompleted());
    }
  }, [registeredUser]);

  useEffect(() => {
    if (registrationError) {
      toast.error(<ToastMessage title="Error" message={registrationError} />);
    }
  }, [registrationError]);

  return (
    <>
      <div className="flex min-h-full flex-col justify-center px-6 py-12 lg:px-8">
        <div className="sm:mx-auto sm:w-full sm:max-w-sm">
          <img alt="Your Company" src="https://tailwindcss.com/plus-assets/img/logos/mark.svg?color=indigo&shade=500" className="mx-auto h-10 w-auto" />
          <h2 className="mt-10 text-center text-2xl/9 font-bold tracking-tight text-white">Register your account</h2>
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
                  className="block w-full rounded-md bg-white/5 px-3 py-1.5 text-base text-white outline-1 -outline-offset-1 outline-white/10 placeholder:text-gray-500 focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-500 sm:text-sm/6"
                  {...register('email', { required: true, maxLength: 20 })}
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
                  className="block w-full rounded-md bg-white/5 px-3 py-1.5 text-base text-white outline-1 -outline-offset-1 outline-white/10 placeholder:text-gray-500 focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-500 sm:text-sm/6"
                  {...register('password', { required: true, maxLength: 25 })}
                />
              </div>
            </div>

            <div>
              <div className="flex items-center justify-between">
                <label htmlFor="confirm-password" className="block text-sm/6 font-medium text-gray-100">
                  Confirm Password
                </label>
              </div>
              <div className="mt-2">
                <input
                  id="confirm-password"
                  type="password"
                  className="block w-full rounded-md bg-white/5 px-3 py-1.5 text-base text-white outline-1 -outline-offset-1 outline-white/10 placeholder:text-gray-500 focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-500 sm:text-sm/6"
                  {...register('confirmPassword', { required: true, maxLength: 25 })}
                />
              </div>
            </div>

            <div>
              <button
                type="submit"
                className="flex w-full justify-center rounded-md bg-indigo-500 px-3 py-1.5 text-sm/6 font-semibold text-white hover:bg-indigo-400 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500"
                disabled={registrationProcessing}
              >
                Register
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

export default LoginPage;
