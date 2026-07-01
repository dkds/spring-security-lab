import React from 'react';
import { Provider } from 'react-redux';
import { PersistGate } from 'redux-persist/integration/react';
import RootComponent from './RootComponent';
import { persistor, store } from './store';
import { setupInterceptor } from './utility/http-client';

const App: React.FC = () => {
  // const loggedInUser = useAppSelector(selectLoggedInUser);
  // console.log(loggedInUser);
  setupInterceptor(store);

  return (
    <Provider store={store}>
      <PersistGate loading={null} persistor={persistor}>
        <RootComponent />
      </PersistGate>
    </Provider>
  );
};

export default App;
