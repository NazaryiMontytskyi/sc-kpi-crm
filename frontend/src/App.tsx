import { Route, Routes } from 'react-router-dom';
import { RequireAuth, RequirePermission } from './auth/guards';
import { AppLayout } from './layout/AppLayout';
import { appRoutes } from './layout/routes';
import { NotFoundPage } from './pages/ErrorPages';

/** Application routes: everything lives inside the authenticated shell; unknown paths show 404. */
export function App() {
  return (
    <Routes>
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
