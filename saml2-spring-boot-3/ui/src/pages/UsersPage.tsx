import React, { useEffect } from 'react';
import { NavLink, useNavigate } from 'react-router';
import { ROUTES } from '~/resources/routes-constants';
import { useAppDispatch, useAppSelector } from '~/store';
import { setSelectedUser } from '~/store/actions';
import { listUsers } from '~/store/actions/thunk-actions';
import { selectUsers } from '~/store/reducers/data';
import { User } from '~/types/common';
import { useLoggedInUser } from '~/utility/functions';

const UsersPage: React.FC = () => {
  useLoggedInUser();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const users = useAppSelector(selectUsers);

  useEffect(() => {
    dispatch(listUsers());
  }, []);

  const handleProvidersClick = (user: User) => {
    dispatch(setSelectedUser(user));
    navigate(ROUTES.PROVIDERS);
  };

  return (
    <div className="relative w-full flex justify-center items-center flex-col mt-10">
      <h1 className="text-white text-[2em]">Users</h1>
      <div className="mt-12 mb-6 w-lg">
        <ul role="list" className="divide-y divide-gray-100">
          {users.map((user) => (
            <li key={user.email} className="flex justify-between gap-x-6 py-2">
              <div className="flex min-w-0 gap-x-4">
                <div className="min-w-0 flex-auto">
                  <p className="mt-1 truncate text-sm/4 text-gray-100">{user.email}</p>
                </div>
              </div>
              <div className="shrink-0 sm:flex sm:flex-col sm:items-end">
                <p className="text-xs/8 text-gray-300">{user.role}</p>
              </div>
              <div className="shrink-0 sm:flex sm:flex-col sm:items-end">
                <button
                  className="bg-blue-500 hover:bg-blue-700 text-white font-bold py-1 px-4 border border-blue-700 rounded text-sm"
                  onClick={() => handleProvidersClick(user)}
                >
                  Providers...
                </button>
              </div>
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

export default UsersPage;
