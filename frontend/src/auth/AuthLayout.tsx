import { Center, Paper, Stack, Text, Title } from '@mantine/core';
import type { ReactNode } from 'react';
import { BrandLogo } from '../brand/BrandLogo';
import { LanguageSwitch } from './LanguageSwitch';

interface AuthLayoutProps {
  title: string;
  subtitle: string;
  children: ReactNode;
}

/**
 * Centered card of the forced change-password screen. Works from 375 px. The sign-in page has its own layout over
 * the photo background (`login/components/LoginShell.tsx`).
 */
export function AuthLayout({ title, subtitle, children }: AuthLayoutProps) {
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
        <LanguageSwitch />
      </Stack>
    </Center>
  );
}
