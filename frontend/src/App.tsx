import { Route, Routes } from 'react-router-dom';
import { ChangePasswordPage } from './auth/ChangePasswordPage';
import { LoginPage } from './auth/LoginPage';
import { RequireAuth, RequirePermission } from './auth/guards';
import { AppLayout } from './layout/AppLayout';
import { appRoutes } from './layout/routes';
import { NotFoundPage } from './pages/ErrorPages';

/** Application routes: everything lives inside the authenticated shell; unknown paths show 404. */
export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/change-password" element={<ChangePasswordPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<AppLayout />}>
          {appRoutes.map((r) => (
            <Route
              key={r.path}
              path={r.path}
              element={
                r.nav?.permission ? (
                  <RequirePermission permission={r.nav.permission}>{r.element}</RequirePermission>
                ) : (
                  r.element
                )
              }
            />
          ))}
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  );
}
