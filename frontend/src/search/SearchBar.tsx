import { ActionIcon, Anchor, Group, Kbd, Modal, Stack, Text, TextInput, UnstyledButton, VisuallyHidden } from '@mantine/core';
import { useDisclosure, useHotkeys, useMediaQuery } from '@mantine/hooks';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';
import { MIN_QUERY_LENGTH, PREVIEW_LIMIT } from './searchApi';
import { SearchResults } from './SearchResults';
import { useDebouncedValue } from './useSearch';

function SearchIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.5-3.5" />
    </svg>
  );
}

/** Header search: a button (Ctrl/Cmd+K or "/" shortcut) opening a search dialog; full screen on mobile. */
export function SearchBar() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [opened, { open, close: closeDialog }] = useDisclosure(false);
  const [value, setValue] = useState('');
  const debounced = useDebouncedValue(value);
  const isMobile = useMediaQuery('(max-width: 48em)') ?? false;

  useHotkeys([
    [
      'mod+K',
      (e) => {
        e.preventDefault();
        open();
      },
    ],
    [
      '/',
      (e) => {
        e.preventDefault();
        open();
      },
    ],
  ]);

  const close = () => {
    closeDialog();
    setValue('');
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const q = value.trim();
    if (q.length < MIN_QUERY_LENGTH) return;
    close();
    navigate(`/search?q=${encodeURIComponent(q)}`);
  };

  const q = debounced.trim();
  return (
    <>
      <UnstyledButton
        visibleFrom="md"
        onClick={open}
        mih={44}
        px="sm"
        aria-label={t('search.open')}
        style={{ border: '1px solid var(--mantine-color-default-border)', borderRadius: 'var(--mantine-radius-md)' }}
      >
        <Group gap="xs" wrap="nowrap">
          <SearchIcon />
          <Text size="sm" c="dimmed" miw={140}>
            {t('search.placeholder')}
          </Text>
          <Kbd size="xs">Ctrl K</Kbd>
        </Group>
      </UnstyledButton>
      <ActionIcon hiddenFrom="md" variant="subtle" color="gray" size={44} onClick={open} aria-label={t('search.open')}>
        <SearchIcon />
      </ActionIcon>
      <Modal
        opened={opened}
        onClose={close}
        fullScreen={isMobile}
        size="lg"
        title={t('search.title')}
        closeButtonProps={{ 'aria-label': t('search.close'), size: 'lg' }}
      >
        <Stack gap="md">
          <form onSubmit={submit} role="search">
            <TextInput
              data-autofocus
              size="md"
              type="search"
              value={value}
              onChange={(e) => setValue(e.currentTarget.value)}
              placeholder={t('search.placeholder')}
              aria-label={t('search.inputLabel')}
              leftSection={<SearchIcon />}
              styles={{ input: { minHeight: 44 } }}
            />
            <VisuallyHidden>
              <button type="submit">{t('search.submit')}</button>
            </VisuallyHidden>
          </form>
          <SearchResults query={debounced} limit={PREVIEW_LIMIT} onNavigate={close} />
          {q.length >= MIN_QUERY_LENGTH && (
            <Anchor
              component={Link}
              to={`/search?q=${encodeURIComponent(q)}`}
              onClick={close}
              mih={44}
              style={{ display: 'flex', alignItems: 'center' }}
            >
              {t('search.showAll')}
            </Anchor>
          )}
        </Stack>
      </Modal>
    </>
  );
}
