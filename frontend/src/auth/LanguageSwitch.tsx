import { SegmentedControl, type SegmentedControlProps } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { changeLanguage, isLanguage } from '../i18n';
import { useAuth } from './CurrentUser';

type LanguageSwitchProps = Pick<SegmentedControlProps, 'classNames' | 'color' | 'size' | 'fullWidth'>;

/**
 * UA / EN switch of the sign-in and change-password screens. Anonymous users switch locally (localStorage);
 * a signed-in user's choice is stored in the profile. Texts update without reload.
 */
export function LanguageSwitch(props: LanguageSwitchProps) {
  const { t, i18n } = useTranslation();
  const { changeLocale, status } = useAuth();
  return (
    <SegmentedControl
      {...props}
      aria-label={t('common.language')}
      value={isLanguage(i18n.language) ? i18n.language : 'uk'}
      onChange={(v) => {
        if (!isLanguage(v)) return;
        void (status === 'authenticated' ? changeLocale(v) : changeLanguage(v));
      }}
      data={[
        { value: 'uk', label: t('common.languageUk') },
        { value: 'en', label: t('common.languageEn') },
      ]}
    />
  );
}
