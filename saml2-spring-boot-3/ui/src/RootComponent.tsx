import React from 'react';
import { Route, HashRouter as Router, Routes } from 'react-router';
import 'tailwindcss/index.css';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import NotFoundPage from './pages/NotFoundPage';
import ProviderManagePage from './pages/ProviderManagePage';
import ProvidersPage from './pages/ProvidersPage';
import RegisterPage from './pages/RegisterPage';
import SSOLoginExchangePage from './pages/SSOLoginExchangePage';
import SSOLoginInitPage from './pages/SSOLoginInitPage';
import SSOLoginSelectPage from './pages/SSOLoginSelectPage';
import UsersPage from './pages/UsersPage';
import { ROUTES } from './resources/routes-constants';
import './styles/main.scss';

const RootComponent: React.FC = () => {
  return (
    <Router>
      <Routes>
        <Route path="*" element={<NotFoundPage />} />
        <Route path={ROUTES.HOMEPAGE} element={<HomePage />} />
        <Route path={ROUTES.USER_LOGIN} element={<LoginPage />} />
        <Route path={ROUTES.USER_REGISTER} element={<RegisterPage />} />
        <Route path={ROUTES.SSO_LOGIN_INIT} element={<SSOLoginInitPage />} />
        <Route path={ROUTES.SSO_LOGIN_SELECT} element={<SSOLoginSelectPage />} />
        <Route path={ROUTES.SSO_LOGIN_EXCHANGE} element={<SSOLoginExchangePage />} />
        <Route path={ROUTES.USERS} element={<UsersPage />} />
        <Route path={ROUTES.PROVIDERS} element={<ProvidersPage />} />
        <Route path={ROUTES.MANAGE_PROVIDERS} element={<ProviderManagePage />} />
      </Routes>
    </Router>
  );
};

export default RootComponent;
