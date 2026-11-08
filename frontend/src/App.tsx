import { Route, Routes } from 'react-router';

import { AuthProvider } from './auth/AuthProvider';
import { Layout } from './components/Layout';
import { RequireAdmin, RequireAuth } from './components/RequireAuth';
import { AccountsPage } from './pages/AccountsPage';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { MembersPage } from './pages/MembersPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { SettingsPage } from './pages/SettingsPage';

export function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route element={<Layout />}>
          <Route path="login" element={<LoginPage />} />
          <Route
            index
            element={
              <RequireAuth>
                <HomePage />
              </RequireAuth>
            }
          />
          <Route
            path="contas"
            element={
              <RequireAuth>
                <AccountsPage />
              </RequireAuth>
            }
          />
          <Route
            path="configuracoes"
            element={
              <RequireAuth>
                <SettingsPage />
              </RequireAuth>
            }
          />
          <Route
            path="membros"
            element={
              <RequireAuth>
                <RequireAdmin>
                  <MembersPage />
                </RequireAdmin>
              </RequireAuth>
            }
          />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </AuthProvider>
  );
}
