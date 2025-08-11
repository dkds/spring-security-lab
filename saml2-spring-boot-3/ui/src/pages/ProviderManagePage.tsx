import { PencilIcon } from '@heroicons/react/24/solid';
import React, { useEffect } from 'react';
import { SubmitHandler, useForm } from 'react-hook-form';
import { NavLink, useNavigate } from 'react-router';
import { toast, ToastContainer } from 'react-toastify';
import ToastMessage from '~/components/ToastMessage';
import { ROUTES } from '~/resources/routes-constants';
import { useAppDispatch, useAppSelector } from '~/store';
import { saveAuthProvider } from '~/store/actions/thunk-actions';
import {
  selectAuthProviderSaveError,
  selectAuthProviderSaveStatus,
  selectSelectedAuthProvider,
} from '~/store/reducers/data';
import { APIStatus } from '~/types/common';
import { useLoggedInUser } from '~/utility/functions';

type Inputs = {
  name: string;
  metadataLocation: string;
};

const ProviderManagePage: React.FC = () => {
  useLoggedInUser();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const editingAuthProvider = useAppSelector(selectSelectedAuthProvider);
  const authProvidersSaveStatus = useAppSelector(selectAuthProviderSaveStatus);
  const authProvidersError = useAppSelector(selectAuthProviderSaveError);
  const { register, handleSubmit, setValue } = useForm<Inputs>();

  const onSubmit: SubmitHandler<Inputs> = (data) => {
    dispatch(saveAuthProvider(data));
  };

  useEffect(() => {
    if (authProvidersSaveStatus === APIStatus.ERROR && authProvidersError) {
      toast.error(<ToastMessage title="Error" message={authProvidersError} />);
    }
    if (authProvidersSaveStatus === APIStatus.SUCCESS) {
      navigate(ROUTES.PROVIDERS);
    }
  }, [authProvidersSaveStatus, authProvidersError]);

  return (
    <>
      <div className="relative w-full flex justify-center items-center flex-col mt-10">
        <h1 className="text-white text-[2em]">Providers</h1>
        <h3 className="text-white text-[1em]">
          {editingAuthProvider ? 'Edit auth provider' : 'Add auth provider'}
        </h3>

        <div className="mt-10 sm:mx-auto sm:w-full sm:max-w-sm">
          <form action="#" method="POST" className="space-y-6" onSubmit={handleSubmit(onSubmit)}>
            <div>
              <label htmlFor="name" className="block text-sm/6 font-medium text-gray-100">
                Provider name
              </label>
              <div className="mt-2">
                <input
                  id="name"
                  className="block w-full rounded-md bg-white/5 px-3 py-1.5 text-base text-white outline-1 -outline-offset-1 outline-white/10 placeholder:text-gray-500 focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-500 sm:text-sm/6"
                  {...register('name', { required: true, maxLength: 20 })}
                  // value={editingAuthProvider?.name || ''}
                />
              </div>
            </div>

            <div>
              <div className="flex items-center justify-between">
                <label
                  htmlFor="metadataLocation"
                  className="block text-sm/6 font-medium text-gray-100"
                >
                  Metadata location
                </label>
              </div>
              <div className="mt-2">
                <input
                  id="metadataLocation"
                  className="block w-full rounded-md bg-white/5 px-3 py-1.5 text-base text-white outline-1 -outline-offset-1 outline-white/10 placeholder:text-gray-500 focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-500 sm:text-sm/6"
                  {...register('metadataLocation', { required: true })}
                  // value={editingAuthProvider?.metadataLocation || ''}
                />
              </div>
            </div>

            <div>
              <button
                type="submit"
                className="flex w-full justify-center rounded-md bg-indigo-500 px-3 py-1.5 text-sm/6 font-semibold text-white hover:bg-indigo-400 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500"
              >
                Save
              </button>
            </div>

            <div className="float-right">
              <NavLink to={ROUTES.PROVIDERS} className="text-blue-300">
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

export default ProviderManagePage;
