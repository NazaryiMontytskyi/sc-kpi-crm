import { Badge } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { STATE_COLORS } from './resolutionLogic';
import type { ResolutionState } from './resolutionsApi';

/** Resolution state as a badge; colors are status colors, never the brand color. */
export function StateBadge({ state }: { state: ResolutionState }) {
  const { t } = useTranslation();
  return (
    <Badge color={STATE_COLORS[state]} variant={state === 'CANCELED' ? 'outline' : 'light'}>
      {t(`tasks.resolutions.state.${state}`)}
    </Badge>
  );
}
