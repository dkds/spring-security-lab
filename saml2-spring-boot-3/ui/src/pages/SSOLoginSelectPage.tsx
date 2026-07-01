import { ArrowRightIcon } from '@heroicons/react/24/solid';
import React, { useEffect } from 'react';
import { NavLink } from 'react-router';
import { ToastContainer } from 'react-toastify';
import { ROUTES } from '~/resources/routes-constants';
import { useAppDispatch, useAppSelector } from '~/store';
import { ssoInitReset } from '~/store/actions';
import { selectUserAuthProviders } from '~/store/reducers/data';
import { AuthProvider } from '~/types/common';

const SSOLoginSelectPage: React.FC = () => {
  const dispatch = useAppDispatch();
  const userAuthProviders = useAppSelector(selectUserAuthProviders);

  useEffect(() => {
    dispatch(ssoInitReset());
  }, []);

  const handleRedirectToProviderOnClick = (provider: AuthProvider) => {
    window.location.href = provider.redirectPath;
    // const ssoRequest = { username: selectedSSOUsername, providerName: provider.name } as SSORequest;
    // dispatch(initiateSSO(ssoRequest));
  };

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
          <ul role="list" className="divide-y divide-gray-100">
            {userAuthProviders.map((provider) => (
              <li key={provider.name} className="flex justify-between gap-x-6 py-2">
                <div className="flex min-w-0 gap-x-4">
                  <div className="min-w-0 flex-auto">
                    <p className="mt-1 truncate text-sm/4 text-gray-100">{provider.name}</p>
                  </div>
                </div>
                <div className="shrink-0 sm:flex sm:flex-col sm:items-end">
                  <p className="text-xs/8 text-gray-300">{provider.redirectPath}</p>
                </div>
                <div className="shrink-0 sm:flex sm:flex-col sm:items-end">
                  <ArrowRightIcon
                    className="size-5 mt-1 text-blue-400 cursor-pointer"
                    onClick={() => handleRedirectToProviderOnClick(provider)}
                  />
                </div>
              </li>
            ))}
          </ul>
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

export default SSOLoginSelectPage;
