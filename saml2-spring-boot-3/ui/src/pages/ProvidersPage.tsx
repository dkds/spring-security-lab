import { MinusIcon, PencilIcon, PlusIcon } from '@heroicons/react/24/solid';
import React, { useEffect } from 'react';
import { NavLink, useNavigate } from 'react-router';
import { ROUTES } from '~/resources/routes-constants';
import { useAppDispatch, useAppSelector } from '~/store';
import { setSelectedAuthProvider } from '~/store/actions';
import { addAuthProvidersToUser, listAuthProviders, listUserAuthProviders } from '~/store/actions/thunk-actions';
import { selectAuthProviders, selectSelectedUser } from '~/store/reducers/data';
import { AuthProvider } from '~/types/common';
import { useLoggedInUser } from '~/utility/functions';

const ProvidersPage: React.FC = () => {
  useLoggedInUser();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const user = useAppSelector(selectSelectedUser);
  const authProviders = useAppSelector(selectAuthProviders);

  useEffect(() => {
    if (user) {
      dispatch(listUserAuthProviders(user.email));
    }
  }, []);

  useEffect(() => {
    dispatch(listAuthProviders());
  }, []);

  const inUser = (provider: AuthProvider): boolean => {
    if (!user || !user.providers) {
      return false;
    }
    return user.providers.some((p) => p.name === provider.name);
  };

  const handleAddAuthProviderOnClick = (): void => {
    console.log('Adding provider');
    dispatch(setSelectedAuthProvider(null));
    navigate(ROUTES.MANAGE_PROVIDERS);
  };
  const handleEditProviderOnClick = (provider: AuthProvider): void => {
    console.log('Editing provider:', provider);
    dispatch(setSelectedAuthProvider(provider));
    navigate(ROUTES.MANAGE_PROVIDERS);
  };
  const handleAddAuthProviderToUserOnClick = (provider: AuthProvider): void => {
    if (!inUser(provider)) {
      console.log('Adding provider:', user, provider);
      if (user) {
        const userAuthProviders = {
          email: user.email,
          providerName: provider.name,
        };
        dispatch(addAuthProvidersToUser(userAuthProviders));
      }
    }
  };
  const handleRemoveProviderOnClick = (provider: AuthProvider): void => {
    if (inUser(provider)) {
      console.log('Removing provider:', provider);
      // dispatch(removeProviderFromUser(user, provider));
    }
  };

  return (
    <div className="relative w-full flex justify-center items-center flex-col mt-10">
      <h1 className="text-white text-[2em]">Providers</h1>
      {user && <h3 className="text-white text-[1em]">{user.email}</h3>}
      <div className="mt-3 mb-2 flex justify-center flex-col gap-3 space-x-4">
        <button
          type="button"
          className="flex w-full justify-center rounded-md bg-indigo-500 px-3 py-1.5 text-sm/6 font-semibold text-white hover:bg-indigo-400 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500"
          onClick={handleAddAuthProviderOnClick}
        >
          Add Provider...
        </button>
      </div>
      <div className="mt-12 mb-6 w-2xl">
        <ul role="list" className="divide-y divide-gray-100">
          {authProviders.map((provider) => (
            <li key={provider.name} className="flex justify-between gap-x-6 py-2">
              <div className="flex min-w-0 gap-x-4">
                <div className="min-w-0 flex-auto">
                  <p className="mt-1 truncate text-sm/4 text-gray-100">{provider.name}</p>
                </div>
              </div>
              <div className="shrink-0 sm:flex sm:flex-col sm:items-end">
                <p className="text-xs/8 text-gray-300">{provider.metadataLocation}</p>
              </div>
              <div className="shrink-0 sm:flex sm:flex-col sm:items-end">
                <PencilIcon className="size-5 mt-1 text-blue-400 cursor-pointer" onClick={() => handleEditProviderOnClick(provider)} />
              </div>
              {user && (
                <div className="shrink-0 sm:flex sm:flex-col sm:items-end">
                  {inUser(provider) ? (
                    <MinusIcon className="size-6 mt-1 text-blue-400 cursor-pointer" onClick={() => handleRemoveProviderOnClick(provider)} />
                  ) : (
                    <PlusIcon className="size-6 mt-1 text-blue-400 cursor-pointer" onClick={() => handleAddAuthProviderToUserOnClick(provider)} />
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>
      </div>
      <div className="mt-6 mb-2 space-x-4">
        <div className="float-right">
          <NavLink to="/" className="text-blue-300">
            Home
          </NavLink>
        </div>
      </div>
    </div>
  );
};

export default ProvidersPage;
