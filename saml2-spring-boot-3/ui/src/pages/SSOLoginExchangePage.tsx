import React, { useEffect } from 'react';
import { NavLink, useNavigate, useSearchParams } from 'react-router';
import { ToastContainer } from 'react-toastify';
import { ROUTES } from '~/resources/routes-constants';
import { useAppDispatch, useAppSelector } from '~/store';
import { exchangeAuthCode } from '~/store/actions/thunk-actions';
import { selectLoggedInUser, selectSSOUsername } from '~/store/reducers/user';

const SSOLoginExchangePage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const dispatch = useAppDispatch();
  const ssoUsername = useAppSelector(selectSSOUsername);
  const loggedInUser = useAppSelector(selectLoggedInUser);

  useEffect(() => {
    const code = searchParams.get('code');
    console.log('code', code);
    if (code && ssoUsername) {
      dispatch(exchangeAuthCode({ username: ssoUsername, code }));
    }
  }, []);

  useEffect(() => {
    if (loggedInUser) {
      navigate(ROUTES.HOMEPAGE);
    }
  }, [loggedInUser]);

  return (
    <>
      <div className="relative w-full flex justify-center items-center flex-col mt-10">
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

        <div className="mt-12 mb-6 w-2xl">
          <ul role="list" className="divide-y divide-gray-100"></ul>
        </div>
        <div className="mt-12 mb-6 w-2xl space-x-4">
          <div className="float-right">
            <NavLink to={ROUTES.SSO_LOGIN_INIT} className="text-blue-300">
              Back
            </NavLink>
          </div>
        </div>
      </div>
      <ToastContainer />
    </>
  );
};

export default SSOLoginExchangePage;
