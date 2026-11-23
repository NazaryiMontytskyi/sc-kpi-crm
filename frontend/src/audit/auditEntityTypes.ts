/**
 * Entity types the backend currently writes to the audit log, each with its own label (`audit.entityTypes.<type>`).
 * Past rows keep the type they were written with and are never rewritten (spec RR-7), so the log may contain types
 * of retired features or types added later; those get the generic `audit.entityTypes.OTHER` label with the raw type.
 */
export const AUDIT_ENTITY_TYPES = [
  'User',
  'MemberProfile',
  'Faculty',
  'ActivityLevelDefinition',
  'ActivityLevelChange',
  'OrgUnit',
  'Position',
  'Appointment',
  'Membership',
  'Role',
  'RoleAssignment',
  'Space',
  'SpaceStatus',
  'CustomFieldDefinition',
  'SavedFilter',
  'Task',
  'TaskAssignee',
  'TaskArtifact',
  'Comment',
  'Article',
  'ArticleVersion',
  'OnboardingList',
  'FileObject',
  'Notification',
  'SearchIndex',
] as const;

export type AuditEntityType = (typeof AUDIT_ENTITY_TYPES)[number];

const KNOWN: ReadonlySet<string> = new Set(AUDIT_ENTITY_TYPES);

export function isKnownAuditEntityType(type: string): type is AuditEntityType {
  return KNOWN.has(type);
}

/** i18n key and interpolation values of the label of an audit entity type; unknown types use the generic label. */
export function auditEntityTypeLabel(type: string): { key: string; values: { type: string } } {
  return {
    key: isKnownAuditEntityType(type) ? `audit.entityTypes.${type}` : 'audit.entityTypes.OTHER',
    values: { type },
  };
}
