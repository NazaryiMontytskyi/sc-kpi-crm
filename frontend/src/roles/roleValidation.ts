export const NAME_MAX = 100;
export const DESCRIPTION_MAX = 500;
/** The built-in role name is reserved (the backend rejects it case-insensitively). */
const RESERVED = 'admin';

export interface RoleValues {
  name: string;
  description: string;
}

export type RoleErrors = Partial<Record<'name' | 'description', string>>;

/** Client-side checks; values are i18n keys. The backend stays authoritative. */
export function validateRole(values: RoleValues): RoleErrors {
  const errors: RoleErrors = {};
  const name = values.name.trim();
  if (name.length === 0) errors.name = 'roles.errors.nameRequired';
  else if (name.length > NAME_MAX) errors.name = 'roles.errors.nameTooLong';
  else if (name.toLowerCase() === RESERVED) errors.name = 'problems.ROLE_NAME_RESERVED';
  if (values.description.length > DESCRIPTION_MAX) errors.description = 'roles.errors.descriptionTooLong';
  return errors;
}
