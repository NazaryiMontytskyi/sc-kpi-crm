# ADR-0006: Position role binding and role grant source

- Status: Accepted
- Date: 2026-10-11

## Context
INC-019 (CONTEXT.md 2.2, 5.2): a `Position` has default roles; appointing a person grants them, ending the appointment revokes them, but "if the user has the same role from another source it stays". `access.role_assignment` (ADR-0003) had one active row per (user, role, scope) and no notion of why a role was granted, so a revoke by `org` could not be distinguished from a manual revoke, and two appointments binding the same role would collide.

## Decision
- **Grant source on `role_assignment`.** Migration V10 adds nullable `source_type` (max 30 chars) and `source_id`; both NULL means a manual grant (the INC-012 endpoints). The active-uniqueness index now includes the source, so a manual grant and any number of source grants for the same (user, role) coexist as separate rows. Effective permissions still take the union over active rows, so a role stays effective until its last grant is gone. Revoking archives a row (history kept, ADR-0003).
- **Public API (additive).** `access.RoleGrantSource(type, id)` and, on `AccessService`: `grantRole(user, role, source)`, `revokeRole(user, role, source)`, `revokeAllGrantedBy(source)` and `isAssignableRole(role)`. Existing `grantRole/revokeRole(user, role)` keep their behaviour but operate on **manual rows only**: a manual revoke does not remove a role that is also held through an appointment (and vice versa). The Admin role is rejected by all source-aware methods (`ADMIN_ROLE_RESERVED`) and cannot be bound to a position.
- **Appointment is the source** (`APPOINTMENT`, appointment id). Ending revokes by `revokeAllGrantedBy`, i.e. exactly what that appointment granted, independent of later edits of the position's role set. When the position's role set changes, `org` grants/revokes the delta for the current holders from their own appointment sources. Archiving a member ends their appointments in the same transaction (`MemberArchivedListener`), which revokes the grants.
- **Head is derived**, not stored on `OrgUnit`: the unit head is the holder of the unit's active head position (`OrgQueryService.headOfUnit`). A partial unique index allows one active head position per unit and a head position has capacity 1, so there is no second source of truth to keep in sync.
- **Capacity** (default 1, 1..100 [ASSUMPTION]) is enforced by the service under a row lock on the position plus a partial unique index (one active appointment per person and position).
- **Dates** are Kyiv calendar days; start and end dates of an appointment cannot be in the future because role grants take effect immediately.
- **Archive policy** (same style as INC-018): a unit with active appointments cannot be archived (`ORG_UNIT_ARCHIVE_BLOCKED`); a position with active appointments cannot be archived; archived units/positions refuse appointments.

## Consequences
- `role_assignment` rows may be several per (user, role); the per-role assignment count reports distinct users.
- Other modules can reuse `RoleGrantSource` for their own derived grants.
- Known gap: appointing checks only `UserDirectory.exists`, so an archived member can still be appointed until identity/members expose an active-user lookup.
