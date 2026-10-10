import { Button, Center, Stack, Text, Title } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { BrandLogo } from '../brand/BrandLogo';
import { usePageTitle } from '../layout/pageTitle';

interface StatusPageProps {
  titleKey: string;
  textKey: string;
  code?: string;
  retry?: boolean;
}

function StatusPage({ titleKey, textKey, code, retry }: StatusPageProps) {
  const { t } = useTranslation();
  usePageTitle(titleKey);
  return (
    <Center mih="60vh" p="md">
      <Stack align="center" gap="md" maw={420} ta="center">
        <BrandLogo variant="mark" short />
        {code && (
          <Text c="dimmed" fw={700} size="lg">
            {code}
          </Text>
        )}
        <Title order={1} size="h2">
          {t(titleKey)}
        </Title>
        <Text c="dimmed">{t(textKey)}</Text>
        {retry ? (
          <Button size="md" mih={44} onClick={() => window.location.reload()}>
            {t('errors.retry')}
          </Button>
        ) : (
          <Button component={Link} to="/" size="md" mih={44}>
            {t('errors.goHome')}
          </Button>
        )}
      </Stack>
    </Center>
  );
}

export function NotFoundPage() {
  return <StatusPage code="404" titleKey="errors.notFound.title" textKey="errors.notFound.text" />;
}
export function ForbiddenPage() {
  return <StatusPage code="403" titleKey="errors.forbidden.title" textKey="errors.forbidden.text" />;
}
export function OfflinePage() {
  return <StatusPage titleKey="errors.offline.title" textKey="errors.offline.text" retry />;
}
