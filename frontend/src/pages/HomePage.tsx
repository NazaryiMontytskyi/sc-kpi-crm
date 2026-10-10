import { Stack, Text, Title } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { usePageTitle } from '../layout/pageTitle';

export function HomePage() {
  const { t } = useTranslation();
  usePageTitle('nav.home');
  return (
    <Stack gap="md">
      <Title order={1} size="h2">
        {t('home.welcome')}
      </Title>
      <Text c="dimmed">{t('home.description')}</Text>
    </Stack>
  );
}
