import { Select, type SelectProps } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { facultyName } from './facultyApi';
import { useFaculties } from './useFaculties';

interface FacultySelectProps extends Omit<SelectProps, 'data' | 'value' | 'onChange'> {
  value: string | null;
  onChange: (facultyId: string | null) => void;
  /**
   * Extra option kept selectable, e.g. the (possibly archived) faculty already stored on the record being edited:
   * the backend accepts an unchanged archived faculty.
   */
  currentOption?: { value: string; label: string } | null;
}

/**
 * Faculty/institute picker for forms and filters (INC-016/021). Lists active faculties only, names in the
 * current UI language, searchable. Archived faculties are deliberately not offered.
 */
export function FacultySelect({ value, onChange, currentOption, ...rest }: FacultySelectProps) {
  const { t, i18n } = useTranslation();
  const { data, isLoading } = useFaculties(false);
  const options = (data ?? []).map((f) => ({ value: f.id, label: facultyName(f, i18n.language) }));
  if (currentOption && !options.some((o) => o.value === currentOption.value)) options.push(currentOption);
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
