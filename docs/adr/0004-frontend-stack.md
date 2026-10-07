# ADR-0004: Frontend stack, versions and API client

- Status: Accepted
- Date: 2026-10-07

## Context
INC-006 creates the frontend in `frontend/` (React + TypeScript, Vite, Mantine, TanStack Query, React Router, react-i18next). CONTEXT.md §8.1 names React 18; CLAUDE.md asks for current stable versions. Versions below are exact (pinned in `package.json`, locked in `package-lock.json`).

## Decision
| Component | Version |
|---|---|
| Node (engines) | >= 22.12 |
| React / React DOM | 19.3.0 |
| Vite / @vitejs/plugin-react | 8.3.3 / 6.1.2 |
| TypeScript | 5.9.3 |
| Mantine (`core`, `hooks`) | 9.7.1 |
| React Router (`react-router-dom`) | 7.18.4 |
| TanStack Query | 5.104.1 |
| i18next / react-i18next | 26.4.2 / 17.0.16 |
| Vitest / Testing Library (react, jest-dom, user-event) / jsdom | 5.0.3 / 16.3.3, 7.0.1, 14.6.7 / 28.1.0 |
| ESLint / typescript-eslint / react-hooks plugin | 10.12.0 / 8.71.1 / 7.1.1 |
| openapi-typescript / openapi-fetch | 7.13.0 / 0.17.0 |

Notes and deviations:
- React 19 instead of 18 (Mantine 9 requires React >= 19.2).
- TypeScript is pinned to 5.9.x, not 7.x: typescript-eslint 8.71 supports `<6.1` and openapi-typescript 7.13 has a peer `^5`.
- jsdom is pinned to 28.1.0 because newer releases require Node >= 22.13 / 22.22.
- No i18next language detector: the UI language is the stored choice (`localStorage`, key `crm.language`) or `uk`; the browser language is deliberately ignored (Ukrainian is the product default). Profile-based language comes with INC-008.

## API client
- The pinned OpenAPI document is committed at `frontend/openapi/openapi.json`; `npm run api:generate` produces `frontend/src/api/generated/schema.d.ts` offline with openapi-typescript. `npm run api:generate:live` first downloads the spec from a running backend (`OPENAPI_URL`, default `http://localhost:8080/v3/api-docs`; dev/test profile only).
- Runtime client: `openapi-fetch` typed by the generated `paths`, `credentials: 'include'` (session cookie `CRMSESSION`), and a middleware copying the `XSRF-TOKEN` cookie into the `X-XSRF-TOKEN` header on state-changing requests (ADR-0002).
- Chosen over `@hey-api/openapi-ts` (requires Node >= 22.18) and Orval (heavier, generates many files); openapi-typescript is types-only, so the generated file is small and reviewable.
- Same-origin everywhere: Vite dev server proxies `/api` to the backend (`VITE_DEV_PROXY_TARGET`); production uses nginx.

## Brand and colors
- The brand palette `studrada` exists only in `src/theme/colors.ts`. ESLint (`no-restricted-syntax`) and a Vitest scan fail on hex literals anywhere else.
- Dark theme: links use shade 2 (`--mantine-color-anchor`), filled elements shade 5; shade 6 is never used for text/icons on dark surfaces.
- Logos are imported from `assets/brand/` with `import.meta.glob` (no copies); a missing file renders the product name as text.

## Consequences
- Upgrading TypeScript beyond 5.x requires newer typescript-eslint and openapi-typescript releases.
- Changing backend endpoints requires refreshing `openapi.json` and regenerating the client in the same task.
