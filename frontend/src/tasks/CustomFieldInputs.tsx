import { NumberInput, Select, Stack, TextInput } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { fullName } from '../members/memberFormat';
import { useMembers } from '../members/useMembers';
import type { FieldDraft, FieldErrorCode } from './taskLogic';
import type { CustomField } from './tasksApi';

interface Props {
  fields: CustomField[];
  draft: FieldDraft;
  errors: Record<string, FieldErrorCode>;
  onChange: (fieldId: string, value: string) => void;
}

/** One input per custom field, rendered by its type; validation messages come from the parent (`errors`). */
export function CustomFieldInputs({ fields, draft, errors, onChange }: Props) {
  const { t } = useTranslation();
  const members = useMembers(false);
  if (fields.length === 0) return null;
  return (
    <Stack gap="sm" data-testid="custom-fields">
      {fields.map((f) => {
        const value = draft[f.id] ?? '';
        const err = errors[f.id];
        const common = {
          label: f.name,
          required: f.required,
          error: err ? t(`tasks.fieldError.${err}`) : undefined,
        };
        switch (f.type) {
          case 'NUMBER':
            return (
              <NumberInput
                key={f.id}
                {...common}
                value={value === '' ? '' : Number(value)}
                onChange={(v) => onChange(f.id, v === '' ? '' : String(v))}
                decimalSeparator="."
                inputMode="decimal"
                size="md"
              />
            );
          case 'DATE':
            return (
              <TextInput key={f.id} {...common} type="date" value={value} onChange={(e) => onChange(f.id, e.currentTarget.value)} size="md" />
            );
          case 'SELECT':
            return (
              <Select
                key={f.id}
                {...common}
                data={f.options}
                value={value || null}
                onChange={(v) => onChange(f.id, v ?? '')}
                clearable={!f.required}
                size="md"
              />
            );
          case 'PERSON':
            return (
              <Select
                key={f.id}
                {...common}
                data={(members.data ?? []).map((m) => ({ value: m.id, label: fullName(m) }))}
                value={value || null}
                onChange={(v) => onChange(f.id, v ?? '')}
                searchable
                clearable={!f.required}
                nothingFoundMessage={t('tasks.noMembers')}
                size="md"
              />
            );
          default:
            return <TextInput key={f.id} {...common} value={value} onChange={(e) => onChange(f.id, e.currentTarget.value)} size="md" />;
        }
      })}
    </Stack>
  );
}
