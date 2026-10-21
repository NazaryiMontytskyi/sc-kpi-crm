import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { MIN_QUERY_LENGTH, searchAll } from './searchApi';

export const SEARCH_DEBOUNCE_MS = 300;

export function useDebouncedValue<T>(value: T, delay = SEARCH_DEBOUNCE_MS): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(id);
  }, [value, delay]);
  return debounced;
}

export function useSearch(query: string, limit: number) {
  const q = query.trim();
  return useQuery({
    queryKey: ['search', q, limit],
    queryFn: () => searchAll(q, limit),
    enabled: q.length >= MIN_QUERY_LENGTH,
  });
}
