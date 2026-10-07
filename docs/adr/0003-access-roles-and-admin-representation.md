# ADR-0003: Roles, permissions and the hardcoded Admin role

- Status: Accepted
- Date: 2026-10-07

## Context
INC-005 needs the permission catalog, role assignments and authorization wiring. CONTEXT.md section 5.2: only `Admin` is hardcoded and has all permissions; every other role is a named set of permissions built at runtime.

## Decision
- **Catalog in code.** `access.Permission` is an enum with stable string keys (the Spring Security authorities). Keys are never renamed. Stored role permissions are strings; keys no longer in the catalog are ignored on read, unknown keys are rejected on write.
- **Admin has no `role` row.** It is the reserved id `AccessService.ADMIN_ROLE_ID` (`00000000-0000-0000-0000-000000000001`). An Admin is a user with an active `role_assignment` whose `role_id` is that id; effective permissions of an Admin are `Permission.allKeys()`, computed in code, so Admin stays complete when permissions are added and cannot be edited, because there is nothing to edit. A DB check prevents a `role` row with that id or the name "Admin". Consequence: `role_assignment.role_id` has no foreign key (validated in the service); `user_id` has none either to keep `access` independent of the identity schema.
- **Admin is protected in services.** `grantRole`/`revokeRole` (public `AccessService`) reject the Admin id (`ADMIN_ROLE_RESERVED`); `RoleService.update/archive` reject it (`SYSTEM_ROLE_IMMUTABLE`), as well as `is_system` rows. The only way to grant Admin is the internal handler of `FirstAdminCreated` (the bootstrapped first account). A future endpoint to appoint further Admins (INC-012) must require Admin itself.
- **Assignments are soft-deleted** (revoke archives the row) so role history is kept; one active assignment per (user, role, scope). `scope_org_unit_id` exists but is unused [ASSUMPTION].
- **Effective permissions** = union over active assignments to non-archived roles (+ all keys for Admin). `access` implements `identity.PermissionsProvider`; identity turns the keys into authorities on every request and returns them in `/auth/me`.
- **Cache.** In-memory map user -> keys, flushed completely (immediately and after transaction completion) on any role or assignment change; loads racing with a flush are discarded via a version counter. Because identity re-reads permissions on every request, open sessions see changes on the next request. Single instance assumed (as in ADR-0002); multiple instances would need a shared invalidation (e.g. short TTL or event).
- **Method security** (`@EnableMethodSecurity`) is enabled in `access.internal.AccessConfig`; `AccessDeniedException` is mapped to 403 problem+json by `GlobalExceptionHandler` / the security access-denied handler.
- Role and assignment changes publish `ROLE_CHANGE` audit events (entity types `Role`, `RoleAssignment`) with before/after snapshots.
