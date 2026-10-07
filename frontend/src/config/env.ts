/** Typed access to Vite environment variables (see frontend/.env.example). */
export const env = {
  /** Base URL of the backend API. Empty string = same origin (dev proxy / nginx). */
  apiBaseUrl: (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '',
} as const;
