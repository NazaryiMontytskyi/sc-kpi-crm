import { Select, type SelectProps } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { facultyName } from './facultyApi';
import { useFaculties } from './useFaculties';

interface FacultySelectProps extends Omit<SelectProps, 'data' | 'value' | 'onChange'> {
  value: string | null;
  onChange: (facultyId: string | null) => void;
}

/**
 * Faculty/institute picker for forms and filters (INC-016/021). Lists active faculties only, names in the
 * current UI language, searchable. Archived faculties are deliberately not offered.
 */
export function FacultySelect({ value, onChange, ...rest }: FacultySelectProps) {
  const { t, i18n } = useTranslation();
  const { data, isLoading } = useFaculties(false);
  const options = (data ?? []).map((f) => ({ value: f.id, label: facultyName(f, i18n.language) }));
  return (
    <Select
      label={t('faculties.select.label')}
      placeholder={t('faculties.select.placeholder')}
      nothingFoundMessage={t('faculties.select.nothing')}
      searchable
      data={options}
      value={value}
      onChange={onChange}
      {...rest}
      disabled={isLoading || rest.disabled}
    />
  );
}
