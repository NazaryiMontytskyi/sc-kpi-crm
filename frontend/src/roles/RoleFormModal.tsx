import { Alert, Button, Group, Modal, Stack, Text, TextInput, Textarea } from '@mantine/core';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { ApiProblemError, fieldErrorMap, problemMessage } from '../api/problem';
import { PermissionEditor } from './PermissionEditor';
import type { Role } from './roleApi';
import { validateRole, type RoleErrors } from './roleValidation';
import { useRoleMutations } from './useRoles';

const TOUCH = 44;

interface Props {
  /** Role being edited; null creates a new one. The built-in role opens read-only. */
  role: Role | null;
  onClose: () => void;
}

/** Create/edit/view dialog of the role builder: name, description and the grouped permission set. */
export function RoleFormModal({ role, onClose }: Props) {
  const { t } = useTranslation();
  const { create, update } = useRoleMutations();
  const readOnly = role?.system === true;
  const [name, setName] = useState(role?.name ?? '');
  const [description, setDescription] = useState(role?.description ?? '');
  const [permissions, setPermissions] = useState<string[]>(role?.permissions ?? []);
  const [clientErrors, setClientErrors] = useState<RoleErrors>({});
  const [serverNameError, setServerNameError] = useState<string | null>(null);
  const [serverText, setServerText] = useState<string | null>(null);
  const [generalError, setGeneralError] = useState<string | null>(null);
  const mutation = role ? update : create;

  const nameError = clientErrors.name
    ? t(clientErrors.name)
    : (serverNameError ?? undefined);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const errors = validateRole({ name, description });
    setClientErrors(errors);
    setServerNameError(null);
    setServerText(null);
    setGeneralError(null);
    if (Object.keys(errors).length > 0 || readOnly) return;
    const body = { name: name.trim(), description: description.trim() || undefined, permissions };
    const options = {
      onSuccess: onClose,
      onError: (error: unknown) => {
        const code = error instanceof ApiProblemError ? error.code : undefined;
        if (code === 'ROLE_NAME_TAKEN' || code === 'ROLE_NAME_RESERVED' || code === 'ROLE_NAME_INVALID') {
          setServerNameError(t(problemMessage(error).key));
          return;
        }
        const fields = fieldErrorMap(error);
        if (fields.name) setServerNameError(fields.name);
        if (fields.description) setServerText(fields.description);
        if (!fields.name && !fields.description) setGeneralError(problemMessage(error).key);
      },
    };
    if (role) update.mutate({ id: role.id, body }, options);
    else create.mutate(body, options);
  };

  const title = readOnly ? t('roles.viewTitle') : role ? t('roles.editTitle') : t('roles.createTitle');

  return (
    <Modal opened onClose={onClose} title={title} centered size="lg" fullScreen={false}>
      <form onSubmit={submit} noValidate>
        <Stack gap="sm">
          {readOnly && <Alert color="blue">{t('roles.systemReadOnly')}</Alert>}
          {generalError && <Alert color="red">{t(generalError)}</Alert>}
          <TextInput
            label={t('roles.name')}
            value={name}
            onChange={(e) => {
              setName(e.currentTarget.value);
              setClientErrors((c) => ({ ...c, name: undefined }));
              setServerNameError(null);
            }}
            error={nameError}
            disabled={readOnly}
            required
            styles={{ input: { minHeight: TOUCH } }}
            data-autofocus
          />
          <Textarea
            label={t('roles.descriptionLabel')}
            value={description}
            onChange={(e) => setDescription(e.currentTarget.value)}
            error={clientErrors.description ? t(clientErrors.description) : (serverText ?? undefined)}
            disabled={readOnly}
            rows={3}
          />
          <Text fw={600} mt="xs">
            {t('roles.permissions')}
          </Text>
          <PermissionEditor value={permissions} onChange={setPermissions} disabled={readOnly} />
          <Group justify="flex-end" mt="xs">
            <Button variant="default" onClick={onClose} mih={TOUCH}>
              {readOnly ? t('roles.close') : t('roles.cancel')}
            </Button>
            {!readOnly && (
              <Button type="submit" loading={mutation.isPending} mih={TOUCH}>
                {t('roles.save')}
              </Button>
            )}
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
