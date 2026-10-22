import { ApiError, ApiSession } from './api';
import { ADMIN, BASE_URL } from './env';

async function waitForStack(): Promise<void> {
  const deadline = Date.now() + 120_000;
  let last = '';
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`${BASE_URL}/actuator/health`);
      if (response.ok) return;
      last = `HTTP ${response.status}`;
    } catch (error) {
      last = String(error);
    }
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  throw new Error(`The stack at ${BASE_URL} is not healthy (${last}). Start it with: bash deploy/e2e.sh`);
}

/**
 * Makes the bootstrap Admin usable for setup calls: the first run signs in with the bootstrap password and performs
 * the forced change; later runs against the same database find the changed password in place.
 */
export default async function globalSetup(): Promise<void> {
  await waitForStack();
  const session = new ApiSession();
  try {
    const me = await session.login(ADMIN.login, ADMIN.initialPassword);
    if (me.mustChangePassword) await session.changePassword(ADMIN.initialPassword, ADMIN.password);
    return;
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) throw error;
  }
  try {
    await new ApiSession().login(ADMIN.login, ADMIN.password);
  } catch {
    throw new Error(
      'The bootstrap Admin can sign in with neither E2E_ADMIN_PASSWORD nor E2E_ADMIN_NEW_PASSWORD. ' +
        'Use a fresh database (E2E_RESET=1 bash deploy/e2e.sh) or pass the matching passwords.',
    );
  }
}
