import { isFutureDate, isRealDate } from './orgDates';

/** Field -> i18n key of the error; an empty object means valid. */
export type Errors<K extends string> = Partial<Record<K, string>>;

export interface UnitFormValues {
  name: string;
  description: string;
  type: string | null;
  parentId: string | null;
}
export type UnitField = keyof UnitFormValues;

export function validateUnit(
  values: UnitFormValues,
  opts: { withType: boolean; parentRequired: boolean },
): Errors<UnitField> {
  const errors: Errors<UnitField> = {};
  const name = values.name.trim();
  if (!name) errors.name = 'org.validation.required';
  else if (name.length > 200) errors.name = 'org.validation.nameTooLong';
  if (values.description.trim().length > 2000) errors.description = 'org.validation.descriptionTooLong';
  if (opts.withType && !values.type) errors.type = 'org.validation.required';
  if (opts.parentRequired && !values.parentId) errors.parentId = 'org.validation.required';
  return errors;
}

export interface PositionFormValues {
  title: string;
  capacity: number | string;
}
export type PositionField = keyof PositionFormValues;

export function validatePosition(values: PositionFormValues): Errors<PositionField> {
  const errors: Errors<PositionField> = {};
  const title = values.title.trim();
  if (!title) errors.title = 'org.validation.required';
  else if (title.length > 200) errors.title = 'org.validation.nameTooLong';
  const capacity = Number(values.capacity);
  if (values.capacity === '' || !Number.isInteger(capacity) || capacity < 1 || capacity > 100) {
    errors.capacity = 'org.validation.capacity';
  }
  return errors;
}

/**
 * Start or end date of an appointment. Appointment dates cannot be in the future (backend rule
 * APPOINTMENT_DATE_IN_FUTURE); `notBefore` is the start date when ending.
 */
export function validateAppointmentDate(value: string, today: string, notBefore?: string): string | null {
  if (!value) return 'org.validation.required';
  if (!isRealDate(value)) return 'org.validation.dateInvalid';
  if (isFutureDate(value, today)) return 'org.validation.dateFuture';
  if (notBefore && value < notBefore) return 'org.validation.dateBeforeStart';
  return null;
}

/** Membership dates only need to be real calendar dates (the backend allows planned start dates). */
export function validateMembershipDate(value: string, notBefore?: string): string | null {
  if (!value) return 'org.validation.required';
  if (!isRealDate(value)) return 'org.validation.dateInvalid';
  if (notBefore && value < notBefore) return 'org.validation.dateBeforeStart';
  return null;
}
