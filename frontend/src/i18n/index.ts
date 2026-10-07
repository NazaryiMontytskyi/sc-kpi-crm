import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import en from './locales/en.json';
import uk from './locales/uk.json';

export const SUPPORTED_LANGUAGES = ['uk', 'en'] as const;
export type Language = (typeof SUPPORTED_LANGUAGES)[number];
export const DEFAULT_LANGUAGE: Language = 'uk';
export const LANGUAGE_STORAGE_KEY = 'crm.language';

export function isLanguage(value: unknown): value is Language {
  return typeof value === 'string' && (SUPPORTED_LANGUAGES as readonly string[]).includes(value);
}

function initialLanguage(): Language {
  try {
    const stored = globalThis.localStorage?.getItem(LANGUAGE_STORAGE_KEY);
    if (isLanguage(stored)) return stored;
  } catch {
    // storage unavailable: use the default
  }
  return DEFAULT_LANGUAGE;
}

function syncDocument(lng: string) {
  if (typeof document === 'undefined') return;
  document.documentElement.lang = isLanguage(lng) ? lng : DEFAULT_LANGUAGE;
  document.title = i18n.t('app.shortName');
}

/** Persists the choice locally; the profile language (backend) is wired in INC-008. */
export async function changeLanguage(lng: Language) {
  try {
    globalThis.localStorage?.setItem(LANGUAGE_STORAGE_KEY, lng);
  } catch {
    // ignore
  }
  await i18n.changeLanguage(lng);
}

void i18n.use(initReactI18next).init({
  resources: { uk: { translation: uk }, en: { translation: en } },
  lng: initialLanguage(),
  fallbackLng: DEFAULT_LANGUAGE,
  supportedLngs: [...SUPPORTED_LANGUAGES],
  interpolation: { escapeValue: false },
});

i18n.on('languageChanged', syncDocument);
syncDocument(i18n.language);

export default i18n;
