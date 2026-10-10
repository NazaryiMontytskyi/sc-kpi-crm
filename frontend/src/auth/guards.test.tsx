import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { renderWithProviders } from '../test/render';
import { ANONYMOUS_STATE, CurrentUserProvider, hasPermission, type AuthState } from './CurrentUser';
import { RequireAuth, RequirePermission } from './guards';

const user = (permissions: string[], isAdmin = false): AuthState => ({
  status: 'authenticated',
  user: { id: 'u', displayName: 'Test User', isAdmin, permissions },
  signOut: () => undefined,
});

function setup(state: AuthState) {
  return renderWithProviders(
    <CurrentUserProvider value={state}>
      <Routes>
        <Route path="/login" element={<div>login page</div>} />
        <Route element={<RequireAuth />}>
          <Route
            path="/"
            element={
              <RequirePermission permission="tasks.create">
                <div>secret</div>
              </RequirePermission>
            }
          />
        </Route>
      </Routes>
    </CurrentUserProvider>,
  );
}

describe('route guards', () => {
  it('redirects anonymous users to /login', () => {
    setup(ANONYMOUS_STATE);
    expect(screen.getByText('login page')).toBeInTheDocument();
  });
  it('shows 403 without the permission (UI convenience only; backend stays authoritative)', () => {
    setup(user([]));
    expect(screen.queryByText('secret')).not.toBeInTheDocument();
    expect(screen.getByText('403')).toBeInTheDocument();
  });
  it('renders content with the permission', () => {
    setup(user(['tasks.create']));
    expect(screen.getByText('secret')).toBeInTheDocument();
  });
  it('lets Admin through', () => {
    setup(user([], true));
    expect(screen.getByText('secret')).toBeInTheDocument();
  });
  it('hasPermission is false for no user', () => {
    expect(hasPermission(null, 'x')).toBe(false);
  });
});
