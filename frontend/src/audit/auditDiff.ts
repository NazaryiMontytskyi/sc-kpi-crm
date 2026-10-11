export type AuditState = Record<string, unknown> | null | undefined;

export interface FieldChange {
  field: string;
  before: unknown;
  after: unknown;
  /** Field exists only in `after` (e.g. on creation). */
  added: boolean;
  /** Field exists only in `before`. */
  removed: boolean;
}

function same(a: unknown, b: unknown): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

/**
 * Field-level difference between two audit snapshots: only fields that changed, sorted by name.
 * A missing snapshot (creation / removal) counts as an empty one.
 */
export function diffStates(before: AuditState, after: AuditState): FieldChange[] {
  const b = before ?? {};
  const a = after ?? {};
  const fields = [...new Set([...Object.keys(b), ...Object.keys(a)])].sort();
  return fields
    .filter((f) => !same(b[f], a[f]))
    .map((f) => ({
      field: f,
      before: b[f],
      after: a[f],
      added: !(f in b),
      removed: !(f in a),
    }));
}

/** Readable text for a snapshot value; `null` for an absent or null value (the UI shows a dash). */
export function formatAuditValue(value: unknown): string | null {
  if (value === null || value === undefined) return null;
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  return JSON.stringify(value);
}
