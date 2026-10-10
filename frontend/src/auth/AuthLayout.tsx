import { Center, Paper, SegmentedControl, Stack, Text, Title } from '@mantine/core';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { BrandLogo } from '../brand/BrandLogo';
import { changeLanguage, isLanguage } from '../i18n';
import { useAuth } from './CurrentUser';

/** Centered card shared by the sign-in and forced change-password screens. Works from 375 px. */
export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  const { t, i18n } = useTranslation();
  const { changeLocale, status } = useAuth();
  return (
    <Center mih="100dvh" p="md">
      <Stack w="100%" maw={420} gap="md">
        <Paper withBorder radius="md" p={{ base: 'md', sm: 'xl' }}>
          <Stack gap="md">
            <Stack gap="xs" align="center" ta="center">
              <BrandLogo height={48} />
              <Title order={1} size="h3">
                {title}
              </Title>
              <Text c="dimmed" size="sm">
                {subtitle}
              </Text>
            </Stack>
            {children}
          </Stack>
        </Paper>
        <SegmentedControl
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
      </Stack>
    </Center>
  );
}
