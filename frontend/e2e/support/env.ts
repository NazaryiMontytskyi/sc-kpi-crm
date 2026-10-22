/** Connection settings of the stack under test; deploy/e2e.sh exports them with fake throw-away values. */
export const BASE_URL = process.env.E2E_BASE_URL ?? 'http://localhost:8081';

export const ADMIN = {
  login: process.env.E2E_ADMIN_LOGIN ?? 'e2e.admin',
  /** The bootstrap password (CRM_BOOTSTRAP_ADMIN_PASSWORD); must be changed at the first sign-in. */
  initialPassword: process.env.E2E_ADMIN_PASSWORD ?? 'Fake-Bootstrap-7q2m9x',
  /** Password the suite sets for the bootstrap Admin; reused on repeated runs against the same database. */
  password: process.env.E2E_ADMIN_NEW_PASSWORD ?? 'Fake-Changed-4k8t1z',
};

/** Password given to every fake test member after the forced change. */
export const MEMBER_PASSWORD = 'Fake-Member-5h3d8w';
