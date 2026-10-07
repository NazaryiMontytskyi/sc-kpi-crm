import {
  Anchor,
  Container,
  Group,
  SegmentedControl,
  Stack,
  Text,
  Title,
  useMantineColorScheme,
} from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { BrandLogo } from './brand/BrandLogo';
import { changeLanguage, isLanguage } from './i18n';

/** Placeholder page proving theme, i18n and brand fallback. Real screens: INC-007 (shell), INC-008 (auth). */
export function App() {
  const { t, i18n } = useTranslation();
  const { colorScheme, setColorScheme } = useMantineColorScheme();

  return (
    <Container size="sm" py="xl">
      <Stack gap="md">
        <BrandLogo />
        <Title order={1} size="h2">
          {t('scaffold.welcome')}
        </Title>
        <Text c="dimmed">{t('scaffold.description')}</Text>
        <Anchor href="#brand">{t('scaffold.sampleLink')}</Anchor>
        <Group gap="md" align="flex-end">
          <Stack gap={4}>
            <Text size="sm">{t('common.language')}</Text>
            <SegmentedControl
              value={isLanguage(i18n.language) ? i18n.language : 'uk'}
              onChange={(value) => {
                if (isLanguage(value)) void changeLanguage(value);
              }}
              data={[
                { value: 'uk', label: t('common.languageUk') },
                { value: 'en', label: t('common.languageEn') },
              ]}
            />
          </Stack>
          <Stack gap={4}>
            <Text size="sm">{t('common.theme')}</Text>
            <SegmentedControl
              value={colorScheme}
              onChange={(value) => setColorScheme(value as 'light' | 'dark' | 'auto')}
              data={[
                { value: 'light', label: t('common.themeLight') },
                { value: 'dark', label: t('common.themeDark') },
                { value: 'auto', label: t('common.themeAuto') },
              ]}
            />
          </Stack>
        </Group>
      </Stack>
    </Container>
  );
}
