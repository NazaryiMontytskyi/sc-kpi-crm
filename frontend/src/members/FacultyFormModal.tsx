import { Alert, Button, Group, Modal, Stack, TextInput } from '@mantine/core';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { problemMessage } from '../api/problem';
import type { Faculty, FacultyRequest } from './facultyApi';
import { serverFieldErrors, type ServerFieldErrors } from './facultyProblems';
import { validateFaculty, type FacultyErrors, type FacultyField } from './facultyValidation';
import { useFacultyMutations } from './useFaculties';

const TOUCH = 44;

interface Props {
  /** Faculty being edited; null creates a new one. */
  faculty: Faculty | null;
  /** Active faculties, used to attribute a duplicate-name error to the right field. */
  known: readonly Faculty[];
  onClose: () => void;
}

/** Create/edit dialog. Client checks run first; server problems are shown per field. */
export function FacultyFormModal({ faculty, known, onClose }: Props) {
  const { t } = useTranslation();
  const { create, update } = useFacultyMutations();
  const [values, setValues] = useState<FacultyRequest>({
    code: faculty?.code ?? '',
    nameUk: faculty?.nameUk ?? '',
    nameEn: faculty?.nameEn ?? '',
  });
  const [clientErrors, setClientErrors] = useState<FacultyErrors>({});
  const [serverErrors, setServerErrors] = useState<ServerFieldErrors>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const mutation = faculty ? update : create;

  const set = (field: FacultyField, value: string) => {
    setValues((v) => ({ ...v, [field]: value }));
    setClientErrors((e) => ({ ...e, [field]: undefined }));
    setServerErrors((e) => ({ ...e, [field]: undefined }));
  };

  const errorFor = (field: FacultyField): string | undefined => {
    const client = clientErrors[field];
    if (client) return t(client);
    const server = serverErrors[field];
    if (server) return server.key ? t(server.key) : server.text;
    return undefined;
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const errors = validateFaculty(values);
    setClientErrors(errors);
    setServerErrors({});
    setGeneralError(null);
    if (Object.keys(errors).length > 0) return;
    const body = { code: values.code.trim(), nameUk: values.nameUk.trim(), nameEn: values.nameEn.trim() };
    const options = {
      onSuccess: onClose,
      onError: (error: unknown) => {
        const fields = serverFieldErrors(error, body, known);
        setServerErrors(fields);
        // Field-level problems are shown at the fields; anything else becomes a general message.
        if (Object.keys(fields).length === 0) setGeneralError(problemMessage(error).key);
      },
    };
    if (faculty) update.mutate({ id: faculty.id, body }, options);
    else create.mutate(body, options);
  };

  return (
    <Modal opened onClose={onClose} title={faculty ? t('faculties.editTitle') : t('faculties.createTitle')} centered>
      <form onSubmit={submit} noValidate>
        <Stack gap="sm">
          {generalError && <Alert color="red">{t(generalError)}</Alert>}
          <TextInput
            label={t('faculties.code')}
            value={values.code}
            onChange={(e) => set('code', e.currentTarget.value)}
            error={errorFor('code')}
            required
            styles={{ input: { minHeight: TOUCH } }}
            data-autofocus
          />
          <TextInput
            label={t('faculties.nameUk')}
            value={values.nameUk}
            onChange={(e) => set('nameUk', e.currentTarget.value)}
            error={errorFor('nameUk')}
            required
            styles={{ input: { minHeight: TOUCH } }}
          />
          <TextInput
            label={t('faculties.nameEn')}
            value={values.nameEn}
            onChange={(e) => set('nameEn', e.currentTarget.value)}
            error={errorFor('nameEn')}
            required
            styles={{ input: { minHeight: TOUCH } }}
          />
          <Group justify="flex-end" mt="xs">
            <Button variant="default" onClick={onClose} mih={TOUCH}>
              {t('faculties.cancel')}
            </Button>
            <Button type="submit" loading={mutation.isPending} mih={TOUCH}>
              {t('faculties.save')}
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
