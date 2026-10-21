import { Chip, Group, ScrollArea, Stack, Title } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/CurrentUser';
import { usePageTitle } from '../layout/pageTitle';
import { isStateFilter, STATE_FILTERS, stateFilterLabelKey, toMemberResolutionQuery, type StateFilter } from './memberResolutionLogic';
import type { MemberResolutionRole } from './memberResolutionsApi';
import { MemberResolutionList } from './MemberResolutionList';
import { useMemberResolutions } from './useMemberResolutions';

const TOUCH = 44;

function PersonalResolutionsPage({ role, titleKey }: { role: MemberResolutionRole; titleKey: string }) {
  const { t } = useTranslation();
  usePageTitle(titleKey);
  const { user } = useAuth();
  const [params] = useSearchParams();
  const initial = params.get('filter');
  const [filter, setFilter] = useState<StateFilter>(isStateFilter(initial) ? initial : 'ACTIVE');
  const list = useMemberResolutions(user?.id ?? '', toMemberResolutionQuery(role, filter));

  return (
    <Stack gap="md">
      <Title order={1} size="h2">
        {t(titleKey)}
      </Title>
      <ScrollArea type="auto" scrollbars="x" offsetScrollbars>
        <Group gap="xs" wrap="nowrap" role="group" aria-label={t('memberResolutions.filter.label')}>
          {STATE_FILTERS.map((f) => (
            <Chip
              key={f}
              size="md"
              variant="light"
              checked={filter === f}
              onChange={() => isStateFilter(f) && setFilter(f)}
              styles={{ label: { minHeight: TOUCH, display: 'inline-flex', alignItems: 'center', whiteSpace: 'nowrap' } }}
            >
              {t(stateFilterLabelKey(f))}
            </Chip>
          ))}
        </Group>
      </ScrollArea>
      <MemberResolutionList
        query={list}
        emptyKey={role === 'AUTHOR' ? 'memberResolutions.emptyIssued' : 'memberResolutions.empty'}
      />
    </Stack>
  );
}

/** "My resolutions": those I execute (executor or co-executor), with state filters. */
export function MyResolutionsPage() {
  return <PersonalResolutionsPage role="EXECUTOR" titleKey="nav.myResolutions" />;
}

/** "Issued by me": resolutions I authored, with state filters. */
export function IssuedByMePage() {
  return <PersonalResolutionsPage role="AUTHOR" titleKey="nav.issuedByMe" />;
}
