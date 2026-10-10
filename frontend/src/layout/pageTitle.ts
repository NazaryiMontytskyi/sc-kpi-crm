import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';

/** Format: "<Page> · <suffix>"; just the suffix when there is no page name. */
export function formatPageTitle(page: string | undefined, suffix: string): string {
  return page ? `${page} · ${suffix}` : suffix;
}

/** Sets document.title from an i18n key; re-runs on language change. */
export function usePageTitle(titleKey?: string) {
  const { t, i18n } = useTranslation();
  useEffect(() => {
    document.title = formatPageTitle(titleKey ? t(titleKey) : undefined, t('app.titleSuffix'));
  }, [t, i18n.language, titleKey]);
}
