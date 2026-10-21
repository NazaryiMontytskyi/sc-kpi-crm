import { Alert, Loader } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Navigate, useParams } from 'react-router-dom';
import { getResolution } from '../tasks/resolutionsApi';

/** `/resolutions/:id` resolves the resolution's task and opens the task page on the resolutions tab. */
export function ResolutionRedirectPage() {
  const { t } = useTranslation();
  const { id = '' } = useParams();
  const query = useQuery({ queryKey: ['resolution-redirect', id], queryFn: () => getResolution(id) });
  if (query.isError) return <Alert color="red">{t('notifications.resolver.failed')}</Alert>;
  if (!query.data) return <Loader size="sm" aria-label={t('notifications.resolver.loading')} />;
  return <Navigate to={`/tasks/${query.data.taskId}?tab=resolutions`} replace />;
}
