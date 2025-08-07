import React, { useEffect } from 'react';
import { useNavigate } from 'react-router';
import { ROUTES } from '~/resources/routes-constants';
import { useAppDispatch, useAppSelector } from '~/store';
import { setSelectedUser } from '~/store/actions';
import { logoutUser } from '~/store/actions/thunk-actions';
import { selectLoggedInUser, selectSSOUsername } from '~/store/reducers/user';
import { useLoggedInUser } from '~/utility/functions';

const HomePage: React.FC = () => {
  useLoggedInUser();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const loggedInUser = useAppSelector(selectLoggedInUser);
  const ssoUser = useAppSelector(selectSSOUsername);

  useEffect(() => {
    if (!loggedInUser) {
      navigate(ROUTES.USER_LOGIN);
    }
  }, [loggedInUser]);

  const handleUsersClick = (): void => {
    navigate(ROUTES.USERS);
  };
  const handleAuthProvidersClick = (): void => {
    dispatch(setSelectedUser(null));
    navigate(ROUTES.PROVIDERS);
  };
  const handleLogoutClick = (): void => {
    dispatch(logoutUser({ isSSO: !!ssoUser }));
  };

  return (
    <div className="relative w-full flex justify-center items-center flex-col mt-10">
      <h1 className="text-white text-[4em]">Hello world!</h1>
      {/* <DateDisplay /> */}
      <div className="mt-6">
        <p className="text-gray-300">Welcome {loggedInUser?.email}</p>
      </div>
      <div className="mt-12 mb-2 flex justify-center flex-col gap-3 space-x-4">
        <button
          type="button"
          className="flex w-full justify-center rounded-md bg-indigo-500 px-3 py-1.5 text-sm/6 font-semibold text-white hover:bg-indigo-400 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500"
          onClick={handleUsersClick}
        >
          Users
        </button>
        <button
          type="button"
          className="flex w-full justify-center rounded-md bg-indigo-500 px-3 py-1.5 text-sm/6 font-semibold text-white hover:bg-indigo-400 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500"
          onClick={handleAuthProvidersClick}
        >
          Auth Providers
        </button>
        <button
          type="button"
          className="flex w-full justify-center rounded-md bg-indigo-500 px-3 py-1.5 mt-3 text-sm/6 font-semibold text-white hover:bg-indigo-400 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500"
          onClick={handleLogoutClick}
        >
          Logout
        </button>
      </div>
    </div>
  );
};

export default HomePage;
