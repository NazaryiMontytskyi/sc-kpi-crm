import { Stack, TextInput, Title } from '@mantine/core';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { PAGE_LIMIT } from './searchApi';
import { SearchResults } from './SearchResults';
import { useDebouncedValue } from './useSearch';

/** `/search?q=` — full results page, up to 20 hits per type. */
export function SearchPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const urlQ = params.get('q') ?? '';
  const [value, setValue] = useState(urlQ);
  const debounced = useDebouncedValue(value);

  // Adopt external URL changes (e.g. a new query submitted from the header dialog).
  const [prevUrlQ, setPrevUrlQ] = useState(urlQ);
  if (prevUrlQ !== urlQ) {
    setPrevUrlQ(urlQ);
    setValue(urlQ);
  }

  useEffect(() => {
    if (debounced === value && debounced !== urlQ) setParams(debounced ? { q: debounced } : {}, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debounced]);

  return (
    <Stack gap="md">
      <Title order={2}>{t('search.title')}</Title>
      <TextInput
        type="search"
        value={value}
        onChange={(e) => setValue(e.currentTarget.value)}
        placeholder={t('search.placeholder')}
        aria-label={t('search.inputLabel')}
        styles={{ input: { minHeight: 44 } }}
      />
      <SearchResults query={urlQ} limit={PAGE_LIMIT} />
    </Stack>
  );
}
