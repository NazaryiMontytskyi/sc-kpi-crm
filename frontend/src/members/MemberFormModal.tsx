import {
  Alert,
  Button,
  FileInput,
  Group,
  Modal,
  SegmentedControl,
  SimpleGrid,
  Stack,
  Text,
  Textarea,
  TextInput,
} from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { isLanguage } from '../i18n';
import { facultyName } from './facultyApi';
import { FacultySelect } from './FacultySelect';
import { MemberAvatar } from './MemberAvatar';
import { uploadAvatar, type Member } from './memberApi';
import { memberErrorKey, memberFieldErrors, type MemberServerErrors } from './memberProblems';
import { validateMember, type MemberErrors, type MemberField, type MemberFormValues } from './memberValidation';
import { useMemberMutations } from './useMembers';

const TOUCH = 44;
const IMAGE_TYPES = 'image/png,image/jpeg,image/webp,image/gif';

/** `create` and `edit` are HR operations (members.create / members.edit); `self` edits telegram, bio and avatar. */
export type MemberFormMode = 'create' | 'edit' | 'self';

interface Props {
  mode: MemberFormMode;
  /** Member being edited; null when creating. */
  member: Member | null;
  onClose: () => void;
  /** Called after a successful create with the new member and the one-time temporary password. */
  onCreated?: (member: Member, temporaryPassword: string) => void;
}

const blankToUndefined = (v: string): string | undefined => (v.trim() ? v.trim() : undefined);

/**
 * Create / edit / self-edit dialog. Full-screen below 48em (375 px phones), centered dialog above.
 * Client checks run first; server problems are shown per field, anything else as a general message.
 */
export function MemberFormModal({ mode, member, onClose, onCreated }: Props) {
  const { t, i18n } = useTranslation();
  const isMobile = useMediaQuery('(max-width: 47.99em)');
  const mutations = useMemberMutations();
  const [values, setValues] = useState<MemberFormValues>({
    login: '',
    lastName: member?.lastName ?? '',
    firstName: member?.firstName ?? '',
    middleName: member?.middleName ?? '',
    facultyId: member?.faculty?.id ?? null,
    studyGroup: member?.studyGroup ?? '',
    telegram: member?.telegram ?? '',
    joinedAt: member?.joinedAt ?? '',
    bio: member?.bio ?? '',
  });
  const [locale, setLocale] = useState<'uk' | 'en'>(isLanguage(i18n.language) ? i18n.language : 'uk');
  const [avatarFileId, setAvatarFileId] = useState<string | null>(member?.avatarFileId ?? null);
  const [uploading, setUploading] = useState(false);
  const [avatarError, setAvatarError] = useState<string | null>(null);
  const [clientErrors, setClientErrors] = useState<MemberErrors>({});
  const [serverErrors, setServerErrors] = useState<MemberServerErrors>({});
  const [generalError, setGeneralError] = useState<string | null>(null);

  const pending = mutations.create.isPending || mutations.update.isPending || mutations.updateSelf.isPending;
  const hr = mode !== 'self';

  const set = <K extends MemberField>(field: K, value: MemberFormValues[K]) => {
    setValues((v) => ({ ...v, [field]: value }));
    setClientErrors((e) => ({ ...e, [field]: undefined }));
    setServerErrors((e) => ({ ...e, [field]: undefined }));
  };

  const errorFor = (field: MemberField): string | undefined => {
    const client = clientErrors[field];
    if (client) return t(client);
    const server = serverErrors[field];
    if (server) return server.key ? t(server.key) : server.text;
    return undefined;
  };

  const onFile = async (file: File | null) => {
    if (!file) return;
    setAvatarError(null);
    setUploading(true);
    try {
      setAvatarFileId(await uploadAvatar(file));
    } catch (error) {
      setAvatarError(memberErrorKey(error));
    } finally {
      setUploading(false);
    }
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const errors = validateMember(values, { withLogin: mode === 'create', selfOnly: mode === 'self' });
    setClientErrors(errors);
    setServerErrors({});
    setGeneralError(null);
    if (Object.keys(errors).length > 0) return;

    const common = {
      telegram: values.telegram.trim(),
      bio: blankToUndefined(values.bio),
      avatarFileId: avatarFileId ?? undefined,
    };
    const hrFields = {
      lastName: values.lastName.trim(),
      firstName: values.firstName.trim(),
      middleName: blankToUndefined(values.middleName),
      facultyId: values.facultyId ?? '',
      studyGroup: values.studyGroup.trim(),
      joinedAt: values.joinedAt,
    };
    const onError = (error: unknown) => {
      const fields = memberFieldErrors(error);
      setServerErrors(fields);
      if (Object.keys(fields).length === 0) setGeneralError(memberErrorKey(error));
    };

    if (mode === 'create') {
      mutations.create.mutate(
        { ...hrFields, ...common, login: values.login.trim(), locale },
        {
          onSuccess: (result) => {
            // The password lives only in the caller's dialog state; drop it from the mutation cache at once.
            mutations.create.reset();
            onCreated?.(result.member, result.temporaryPassword);
          },
          onError,
        },
      );
    } else if (mode === 'edit' && member) {
      mutations.update.mutate({ id: member.id, body: { ...hrFields, ...common } }, { onSuccess: onClose, onError });
    } else {
      mutations.updateSelf.mutate(common, { onSuccess: onClose, onError });
    }
  };

  const titleKey = mode === 'create' ? 'members.createTitle' : mode === 'edit' ? 'members.editTitle' : 'members.editSelfTitle';
  const currentFaculty = member?.faculty;

  return (
    <Modal opened onClose={onClose} title={t(titleKey)} centered={!isMobile} fullScreen={isMobile} size="lg">
      <form onSubmit={submit} noValidate>
        <Stack gap="sm">
          {generalError && <Alert color="red">{t(generalError)}</Alert>}
          {mode === 'create' && (
            <TextInput
              label={t('members.login')}
              description={t('members.loginHint')}
              value={values.login}
              onChange={(e) => set('login', e.currentTarget.value)}
              error={errorFor('login')}
              required
              autoComplete="off"
              autoCapitalize="none"
              styles={{ input: { minHeight: TOUCH } }}
              data-autofocus
            />
          )}
          {hr && (
            <>
              <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="sm">
                <TextInput
                  label={t('members.lastName')}
                  value={values.lastName}
                  onChange={(e) => set('lastName', e.currentTarget.value)}
                  error={errorFor('lastName')}
                  required
                  styles={{ input: { minHeight: TOUCH } }}
                />
                <TextInput
                  label={t('members.firstName')}
                  value={values.firstName}
                  onChange={(e) => set('firstName', e.currentTarget.value)}
                  error={errorFor('firstName')}
                  required
                  styles={{ input: { minHeight: TOUCH } }}
                />
              </SimpleGrid>
              <TextInput
                label={t('members.middleName')}
                value={values.middleName}
                onChange={(e) => set('middleName', e.currentTarget.value)}
                error={errorFor('middleName')}
                styles={{ input: { minHeight: TOUCH } }}
              />
              <FacultySelect
                value={values.facultyId}
                onChange={(id) => set('facultyId', id)}
                error={errorFor('facultyId')}
                required
                styles={{ input: { minHeight: TOUCH } }}
                currentOption={
                  currentFaculty ? { value: currentFaculty.id, label: facultyName(currentFaculty, i18n.language) } : null
                }
              />
              <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="sm">
                <TextInput
                  label={t('members.studyGroup')}
                  value={values.studyGroup}
                  onChange={(e) => set('studyGroup', e.currentTarget.value)}
                  error={errorFor('studyGroup')}
                  required
                  styles={{ input: { minHeight: TOUCH } }}
                />
                <TextInput
                  type="date"
                  label={t('members.joinedAt')}
                  value={values.joinedAt}
                  onChange={(e) => set('joinedAt', e.currentTarget.value)}
                  error={errorFor('joinedAt')}
                  required
                  styles={{ input: { minHeight: TOUCH } }}
                />
              </SimpleGrid>
            </>
          )}
          <TextInput
            label={t('members.telegram')}
            description={t('members.telegramHint')}
            value={values.telegram}
            onChange={(e) => set('telegram', e.currentTarget.value)}
            error={errorFor('telegram')}
            required
            autoCapitalize="none"
            styles={{ input: { minHeight: TOUCH } }}
          />
          <Textarea
            label={t('members.bio')}
            value={values.bio}
            onChange={(e) => set('bio', e.currentTarget.value)}
            error={errorFor('bio')}
            rows={5}
            resize="vertical"
          />
          <Stack gap="xs">
            <Text size="sm" fw={500}>
              {t('members.avatar')}
            </Text>
            <Group wrap="nowrap" align="center">
              <MemberAvatar
                member={{ lastName: values.lastName, firstName: values.firstName, avatarFileId: member?.avatarFileId ?? null }}
                fileId={avatarFileId}
                size={56}
              />
              <FileInput
                aria-label={t('members.avatarUpload')}
                placeholder={t('members.avatarUpload')}
                accept={IMAGE_TYPES}
                onChange={(f) => void onFile(f)}
                value={null}
                disabled={uploading}
                error={avatarError ? t(avatarError) : undefined}
                style={{ flex: 1, minWidth: 0 }}
                styles={{ input: { minHeight: TOUCH } }}
              />
            </Group>
            {avatarFileId && (
              <Button
                variant="subtle"
                color="gray"
                size="compact-md"
                mih={TOUCH}
                onClick={() => setAvatarFileId(null)}
                w="fit-content"
              >
                {t('members.avatarRemove')}
              </Button>
            )}
          </Stack>
          {mode === 'create' && (
            <Stack gap={4}>
              <Text size="sm" fw={500}>
                {t('members.initialLanguage')}
              </Text>
              <SegmentedControl
                value={locale}
                onChange={(v) => isLanguage(v) && setLocale(v)}
                data={[
                  { value: 'uk', label: t('common.languageUk') },
                  { value: 'en', label: t('common.languageEn') },
                ]}
              />
            </Stack>
          )}
          <Group justify="flex-end" mt="xs">
            <Button variant="default" onClick={onClose} mih={TOUCH}>
              {t('members.cancel')}
            </Button>
            <Button type="submit" loading={pending} disabled={uploading} mih={TOUCH}>
              {t('members.save')}
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
