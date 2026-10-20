import { Select, SegmentedControl, Stack, Text } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { activeUnits } from '../org/orgTree';
import { FacultySelect } from './FacultySelect';
import { SORT_OPTIONS, withFilter, type MemberFilters, type MemberSort, type MemberStatus } from './memberFilters';
import { usePositionOptions } from './useMemberOrg';

const TOUCH = 44;
const INPUT_STYLES = { input: { minHeight: TOUCH } };

const SORT_LABEL_KEYS: Record<MemberSort, string> = {
  'lastName,asc': 'members.directory.sortNameAsc',
  'lastName,desc': 'members.directory.sortNameDesc',
  'joinedAt,desc': 'members.directory.sortJoinedDesc',
  'joinedAt,asc': 'members.directory.sortJoinedAsc',
};

interface Props {
  filters: MemberFilters;
  onChange: (next: MemberFilters) => void;
  /** Archived members are offered only to members.archive holders (the backend enforces it as well). */
  canSeeArchived: boolean;
}

/**
 * The directory filter fields (INC-021): faculty, department, division, position, status, sort. Every change is
 * applied at once; the activity-level filter has no control yet (levels arrive with INC-022).
 */
export function MemberFiltersPanel({ filters, onChange, canSeeArchived }: Props) {
  const { t } = useTranslation();
  const { options: positions, tree } = usePositionOptions(true);
  const units = activeUnits(tree.data ?? []);
  const unitOptions = (type: string) =>
    units.filter((u) => u.type === type).map((u) => ({ value: u.id, label: u.name }));
  const set = (change: Partial<Omit<MemberFilters, 'page'>>) => onChange(withFilter(filters, change));
  const any = t('members.directory.anyOption');

  return (
    <Stack gap="sm" data-testid="member-filters">
      <FacultySelect
        value={filters.facultyId}
        onChange={(facultyId) => set({ facultyId })}
        clearable
        placeholder={any}
        styles={INPUT_STYLES}
      />
      <Select
        label={t('members.directory.department')}
        description={t('members.directory.departmentHint')}
        placeholder={any}
        searchable
        clearable
        data={unitOptions('DEPARTMENT')}
        value={filters.departmentId}
        onChange={(departmentId) => set({ departmentId })}
        styles={INPUT_STYLES}
      />
      <Select
        label={t('members.directory.division')}
        placeholder={any}
        searchable
        clearable
        data={unitOptions('DIVISION')}
        value={filters.divisionId}
        onChange={(divisionId) => set({ divisionId })}
        styles={INPUT_STYLES}
      />
      <Select
        label={t('members.directory.position')}
        placeholder={any}
        searchable
        clearable
        data={positions.map((p) => ({
          value: p.value,
          label: t('members.directory.positionOption', { title: p.title, unit: p.unitName }),
        }))}
        value={filters.positionId}
        onChange={(positionId) => set({ positionId })}
        styles={INPUT_STYLES}
      />
      {canSeeArchived && (
        <div>
          <Text size="sm" fw={500} mb={4}>
            {t('members.directory.status')}
          </Text>
          <SegmentedControl
            fullWidth
            value={filters.status}
            onChange={(v) => set({ status: v as MemberStatus })}
            data={[
              { value: 'ACTIVE', label: t('members.directory.statusActive') },
              { value: 'ARCHIVED', label: t('members.directory.statusArchived') },
              { value: 'ALL', label: t('members.directory.statusAll') },
            ]}
            styles={{ label: { minHeight: TOUCH, display: 'flex', alignItems: 'center', justifyContent: 'center' } }}
          />
        </div>
      )}
      <Select
        label={t('members.directory.sort')}
        allowDeselect={false}
        data={SORT_OPTIONS.map((s) => ({ value: s, label: t(SORT_LABEL_KEYS[s]) }))}
        value={filters.sort}
        onChange={(sort) => sort && set({ sort: sort as MemberSort })}
        styles={INPUT_STYLES}
      />
    </Stack>
  );
}
