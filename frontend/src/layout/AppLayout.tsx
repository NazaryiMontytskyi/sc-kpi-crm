import {
  ActionIcon,
  AppShell,
  Burger,
  Group,
  Menu,
  NavLink,
  SegmentedControl,
  Stack,
  Text,
  UnstyledButton,
  useMantineColorScheme,
} from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { useTranslation } from 'react-i18next';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { BrandLogo } from '../brand/BrandLogo';
import { hasPermission, useAuth, type CurrentUser } from '../auth/CurrentUser';
import { changeLanguage, isLanguage } from '../i18n';
import { OfflinePage } from '../pages/ErrorPages';
import { appRoutes, type AppRoute } from './routes';
import { useOnlineStatus } from './useOnlineStatus';

/** Minimum touch target in px (WCAG / mobile guidelines). */
const TOUCH = 44;

function BellIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
      <path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.7 21a2 2 0 0 1-3.4 0" />
    </svg>
  );
}

/** Slot for the notification bell; the notifications feature (later stage) replaces the content. */
function NotificationBellSlot() {
  const { t } = useTranslation();
  return (
    <ActionIcon variant="subtle" color="gray" size={TOUCH} aria-label={t('layout.notifications')} data-testid="bell-slot">
      <BellIcon />
    </ActionIcon>
  );
}

function UserMenu({ profileAvailable }: { profileAvailable: boolean }) {
  const { t, i18n } = useTranslation();
  const { colorScheme, setColorScheme } = useMantineColorScheme();
  const { user, signOut } = useAuth();
  return (
    <Menu position="bottom-end" width={260} withinPortal>
      <Menu.Target>
        <UnstyledButton mih={TOUCH} miw={TOUCH} px="xs" aria-label={t('layout.userMenu')}>
          <Text size="sm" fw={500} truncate maw={120}>
            {user?.displayName}
          </Text>
        </UnstyledButton>
      </Menu.Target>
      <Menu.Dropdown>
        {profileAvailable && (
          <Menu.Item component={Link} to="/profile" mih={TOUCH}>
            {t('layout.profile')}
          </Menu.Item>
        )}
        <Stack gap={4} p="xs">
          <Text size="xs" c="dimmed">
            {t('common.language')}
          </Text>
          <SegmentedControl
            fullWidth
            value={isLanguage(i18n.language) ? i18n.language : 'uk'}
            onChange={(v) => {
              if (isLanguage(v)) void changeLanguage(v);
            }}
            data={[
              { value: 'uk', label: t('common.languageUk') },
              { value: 'en', label: t('common.languageEn') },
            ]}
          />
          <Text size="xs" c="dimmed" mt="xs">
            {t('common.theme')}
          </Text>
          <SegmentedControl
            fullWidth
            value={colorScheme}
            onChange={(v) => setColorScheme(v as 'light' | 'dark' | 'auto')}
            data={[
              { value: 'light', label: t('common.themeLight') },
              { value: 'dark', label: t('common.themeDark') },
              { value: 'auto', label: t('common.themeAuto') },
            ]}
          />
        </Stack>
        <Menu.Divider />
        <Menu.Item mih={TOUCH} onClick={signOut}>
          {t('layout.signOut')}
        </Menu.Item>
      </Menu.Dropdown>
    </Menu>
  );
}

/** Routes that have a nav entry the user may see (permission check is a convenience only). */
export function visibleNavRoutes(routes: AppRoute[], user: CurrentUser | null): AppRoute[] {
  return routes.filter((r) => r.nav && (!r.nav.permission || hasPermission(user, r.nav.permission)));
}

export function AppLayout({ routes = appRoutes }: { routes?: AppRoute[] }) {
  const { t } = useTranslation();
  const { user } = useAuth();
  const [opened, { toggle, close }] = useDisclosure(false);
  const location = useLocation();
  const online = useOnlineStatus();
  const items = visibleNavRoutes(routes, user);
  const profileAvailable = routes.some((r) => r.path === '/profile');

  return (
    <AppShell header={{ height: 60 }} navbar={{ width: 260, breakpoint: 'sm', collapsed: { mobile: !opened } }} padding="md">
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between" wrap="nowrap">
          <Group gap="sm" wrap="nowrap">
            <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="md" aria-label={t('layout.menu')} />
            <Link to="/" style={{ color: 'inherit', textDecoration: 'none' }} aria-label={t('nav.home')}>
              <BrandLogo short />
            </Link>
          </Group>
          <Group gap="xs" wrap="nowrap">
            <NotificationBellSlot />
            <UserMenu profileAvailable={profileAvailable} />
          </Group>
        </Group>
      </AppShell.Header>
      <AppShell.Navbar p="xs" aria-label={t('layout.navigation')}>
        {items.map((r) => (
          <NavLink
            key={r.path}
            component={Link}
            to={r.path}
            label={t(r.nav!.labelKey)}
            active={r.path === '/' ? location.pathname === '/' : location.pathname.startsWith(r.path)}
            mih={TOUCH}
            onClick={close}
          />
        ))}
      </AppShell.Navbar>
      <AppShell.Main>{online ? <Outlet /> : <OfflinePage />}</AppShell.Main>
    </AppShell>
  );
}
