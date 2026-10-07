# PLAN.md

## Plan: MVP
Source: CONTEXT.md §9 (MVP), §5.1–5.4, 5.8–5.11, §6, §8; CLAUDE.md "Project setup" (overrides §6.1, §8.1, §8.3) · Created: 2026-10-07

### Overview
Delivers the `[MVP]` stage only: accounts, team records/org, role builder, task tracker with resolutions, knowledge base with onboarding, global search, in-app notifications, audit log, "My resolutions" widget, uk/en, Docker Compose deployment. Meetings, voting, Handover, shared dashboard, calendar view and PWA are `[v2]` and are NOT planned (extension points are noted where relevant).
Phases (by dependency, not strictly by number): A Foundation (001-010) -> B Access/Audit (011-014) -> C Members/Org (052 faculty dictionary first, then 015-023) -> D Tasks core (024-028, 030) -> E Resolutions (032-037, then 029 saved filters and 031 done-suggestion, which depend on INC-032) -> F Knowledge (038-042) -> G Notifications, Search, Widget (043-048) -> H Deployment, brand assets, E2E (049-051). INC-052 is numbered last but scheduled before INC-015.
Repo state found: Maven root project with `pom.xml` (Spring Boot 4.1.1, Java 21 — compliant with CLAUDE.md), Spring Modulith 2.1.1, JPA, Flyway, Security, Validation, webmvc, actuator, Testcontainers, Lombok, `spring-boot-docker-compose`. Missing: MapStruct, springdoc-openapi, project name/description metadata, `application.yml`, ADRs, `deploy/`, `frontend/`, `assets/brand/`. Root `compose.yaml` (Initializr, `postgres:latest`, default creds) must be moved/replaced.
Conventions for ALL increments below (not repeated in each): permissions via `@PreAuthorize` with keys from CONTEXT.md §5.2; every data change emits a domain event consumed by `audit` (§5.10); soft delete only; new Flyway migration per schema change (never edit an old one); uk+en keys together; UI checked at 375 px, light+dark, colors via theme tokens; OpenAPI updated and TS client regenerated; modules talk only via public services/events; domain events for create/update/archive are published so `search` and `notifications` can subscribe.

### Increments

#### INC-001 — Backend scaffold, build ADR, compose relocation
- **Status:** DONE
- **Module(s):** build / repo root (`pom.xml`, `deploy/`, `docs/adr/`)
- **Depends on:** —
- **Scope:** Fix `pom.xml` metadata (name, description; remove empty license/developer/scm blocks); add MapStruct (+ annotation processor next to Lombok, with lombok-mapstruct-binding) and springdoc-openapi (Boot 4 line); keep Spring Modulith; `application.yml` replacing `application.properties` (profiles `dev`, `test`, `prod`; `spring.jpa.open-in-view=false`, `hibernate.ddl-auto=validate`, `hibernate.jdbc.time_zone=UTC`); create base package skeleton `com.kpi.studentcouncil.crm.<module>` for the 13 modules (package-info with Modulith `@ApplicationModule`); move root `compose.yaml` to `deploy/docker-compose.dev.yml` (Postgres 16 pinned, not `latest`, creds via env) and point `spring-boot-docker-compose` at it via `spring.docker.compose.file`; ADR-0001 (Maven chosen, exact versions: Boot 4.1.1, Java 21, Modulith, MapStruct, springdoc, Postgres 16); `README.md` (run locally); update the Commands block need reported to orchestrator.
- **Out of scope:** any domain code, frontend, production compose (INC-049).
- **Acceptance criteria:**
  1. `pom.xml` parent is Spring Boot 4.1.x and `java.version` is 21; MapStruct and springdoc dependencies resolved; `./mvnw verify` passes.
  2. No `compose.yaml` at repo root; `deploy/docker-compose.dev.yml` exists, uses `postgres:16`, no hardcoded real secrets.
  3. `docs/adr/0001-build-tool-and-versions.md` exists and records Maven and the versions in use (CONTEXT.md §8.3).
  4. A `ModularityTests` (Spring Modulith `ApplicationModules.verify()`) test exists and passes against the module skeleton.
  5. Only Java/resources/tests are under `src/` (CLAUDE.md layout).
- **Tests required:** integration (context loads with Testcontainers Postgres 16), Modulith verification.
- **Notes:** Risk: Boot 4 / Modulith 2.x / springdoc compatibility — implementer must pick the springdoc version that targets Boot 4 and record it in ADR.

#### INC-002 — Shared kernel: base entity, soft delete, errors, pagination, backend i18n
- **Status:** DONE
- **Module(s):** backend `shared` (kernel package, not a business module)
- **Depends on:** INC-001
- **Scope:** `BaseEntity` (UUID id, createdAt/By, updatedAt/By via JPA auditing, UTC `Instant`); `ArchivableEntity` (archivedAt/By, `archive()/restore()`, default query filters for non-archived); `/api/v1` prefix convention; RFC 7807 Problem Details global handler (validation, not found, forbidden, conflict, domain-rule violations with stable `code` property); `PageResponse` and pagination params; Spring `MessageSource` with `messages_uk.properties` / `messages_en.properties`, locale resolved from user locale/`Accept-Language`, default `uk`; injectable `Clock`; `AuditorAware` placeholder.
- **Out of scope:** auth (INC-004), audit persistence (INC-003).
- **Acceptance criteria:**
  1. Entities extending `BaseEntity` get id/created/updated fields populated automatically; time stored as UTC (CONTEXT.md §6).
  2. Archiving sets `archivedAt/archivedBy`; archived rows excluded from default list queries; `restore()` clears them; no hard-delete method on the base repository abstraction (§5.11).
  3. Every error response is `application/problem+json` with `type`, `title`, `status`, `detail`, `code`; localized by locale (uk default).
  4. Paginated list endpoints share one response shape.
  5. Unit tests cover archive/restore and problem mapping.
- **Tests required:** unit (archive/restore), integration (error handler through a test controller, locale switching).
- **Notes:** shared-kernel change, own increment per planning rules.

#### INC-003 — Audit pipeline and AuditLog storage
- **Status:** DONE
- **Module(s):** backend `audit`
- **Depends on:** INC-002
- **Scope:** `AuditLog` entity (actorId, action, entityType, entityId, before JSONB, after JSONB, at, ip) with append-only enforcement (no update/delete in repository; DB trigger or revoked privileges forbidding UPDATE/DELETE); public `AuditEvent` record + `AuditPublisher` API other modules use via `ApplicationEventPublisher`; listener persisting events (transactional, same transaction as the change); Flyway migration.
- **Out of scope:** viewing endpoints/UI (INC-023), per-module event emitters (each module's increment).
- **Acceptance criteria:**
  1. Publishing an `AuditEvent` inside a transaction persists exactly one `AuditLog` row; if the transaction rolls back, no row remains.
  2. Actor and client IP are taken from the security context/request; `before`/`after` stored as JSON.
  3. Repository exposes no update/delete; a DB-level guard rejects UPDATE/DELETE on `audit_log` (verified by an integration test).
  4. Actions use a fixed `AuditAction` enum (CREATE, UPDATE, ARCHIVE, RESTORE, LOGIN, LOGIN_FAILED, ROLE_CHANGE, APPOINT, …) (§5.10).
- **Tests required:** unit, integration (Testcontainers).
- **Notes:** actor resolution depends on INC-004; use an `ActorProvider` interface with a system-actor fallback until then.

#### INC-004 — Identity: User, session login, security baseline
- **Status:** DONE
- **Module(s):** backend `identity`
- **Depends on:** INC-002, INC-003
- **Scope:** `User` entity (login, passwordHash, status ACTIVE|ARCHIVED|BLOCKED, locale uk|en, lastLoginAt, `mustChangePassword`); migration; Spring Security config: session in HttpOnly cookie, CSRF protection (cookie token for SPA), BCrypt (or Argon2) hashing, `/api/v1/auth/login`, `/logout`, `/me` (id, login, locale, permissions list placeholder, mustChangePassword), `/auth/change-password`; login attempt limiting (lockout/backoff, configurable); archived/blocked users cannot sign in; forced password change enforced server-side (all other endpoints return 403 problem code `PASSWORD_CHANGE_REQUIRED` until changed); bootstrap of the first `Admin` account from env variables on empty DB; audit events LOGIN, LOGIN_FAILED, PASSWORD_CHANGE; OIDC extension point (`ExternalIdentity` link not implemented, authentication provider kept pluggable — §7 KPI ID `[LATER]`).
- **Out of scope:** account creation by HR (INC-015), roles/permissions (INC-011).
- **Acceptance criteria:**
  1. Correct credentials create a session cookie flagged HttpOnly (+Secure in prod profile); wrong credentials return 401 problem details; response does not reveal whether login exists.
  2. After N failed attempts (default 5, configurable) sign-in is temporarily refused; unit test covers the counter.
  3. ARCHIVED/BLOCKED users cannot sign in even with correct password.
  4. A user with `mustChangePassword=true` can call only `/me`, `/auth/change-password`, `/logout` until the password is changed; new password must differ and satisfy policy.
  5. State-changing requests without CSRF token are rejected; unauthenticated requests to non-public endpoints return 401.
  6. Passwords never appear in logs, audit rows or responses.
  7. First Admin is created from env on empty DB only, with `mustChangePassword=true`; no default credentials in repo.
- **Tests required:** unit (throttling, policy), integration (login/logout, CSRF, forced change, blocked).
- **Notes:** Decision (Q4): every user has an individual password; no shared/group passwords (no shared default password anywhere; each temporary password is random per account). Brute-force protection is mandatory. Thresholds not specified by the owner: defaults = 5 failed attempts per login+IP within 15 min -> 15 min lockout with exponential backoff, min password length 10, session idle timeout 8 h; all configurable via `application.yml`.

#### INC-005 — Access core: Permission catalog, Role, RoleAssignment, authorization wiring
- **Status:** DONE
- **Module(s):** backend `access`
- **Depends on:** INC-004
- **Scope:** `Permission` enum with exactly the keys of CONTEXT.md §5.2 (extensible); `Role` (name, description, permissions, `isSystem`), `RoleAssignment` (userId, roleId, optional `scopeOrgUnitId` column present but unused `[ASSUMPTION]`); hardcoded `Admin` system role (all permissions, cannot be edited/deleted via API); effective permissions = union over assignments (+ Admin); `GrantedAuthority` per permission loaded into the session principal; `@EnableMethodSecurity`; public `AccessService` (hasPermission, effectivePermissions, grantRole/revokeRole for other modules); `/auth/me` returns effective permissions; permission cache invalidated on role/assignment change; migration.
- **Out of scope:** role builder API (INC-012), position-bound roles (INC-019), seed roles (INC-013).
- **Acceptance criteria:**
  1. All permission keys from §5.2 exist in code with stable string keys (e.g. `members.activity.set`); a unit test asserts the full list.
  2. A user with two roles has the union of permissions; a user without roles has none (but still authenticated).
  3. Admin has every permission without database role rows and its role cannot be modified through any service method.
  4. `@PreAuthorize("hasAuthority('x.y')")` denies with 403 problem details for missing permission (test endpoint).
  5. Role/assignment changes emit ROLE_CHANGE audit events with before/after.
- **Tests required:** unit (union, Admin), integration (authorization on test endpoint, cache invalidation).
- **Notes:** Openness: read endpoints in later modules require only authentication unless a view permission is defined in §5.2 (`members.view`, `org.view`, `tasks.view`, `kb.view`); seed roles grant them to everyone, see INC-013.

#### INC-006 — Frontend scaffold: Vite, Mantine theme, i18n, API client
- **Status:** DONE
- **Module(s):** frontend `frontend/` (app, theme, i18n, api)
- **Depends on:** INC-001
- **Scope:** Vite + React + TypeScript project in `frontend/`; Mantine with `studrada` palette exactly as in CLAUDE.md (`primaryColor: 'studrada'`, `primaryShade {light:6, dark:5}`) in `src/theme/colors.ts`; light/dark with neutral surfaces; React Router, TanStack Query; react-i18next with `uk` (default) and `en`, keys `app.name`, `app.shortName` (§6.1); OpenAPI client generation script `npm run api:generate` (from backend `/v3/api-docs`); ESLint, Vitest + Testing Library; Vite dev proxy to backend `/api`; env handling; brand asset loader that falls back to a text placeholder when `assets/brand/` files are missing; `docs`-free.
- **Out of scope:** screens (INC-007, INC-008).
- **Acceptance criteria:**
  1. `npm ci && npm run build && npm run lint && npm test` pass in `frontend/`.
  2. Brand color `#0700CD` appears in exactly one place (`colors.ts`); a lint/test check fails if hex literals are used in components.
  3. Dark theme never uses shade 6 for text/icons (CLAUDE.md contrast rules) — theme sets link/anchor color to shade 2 in dark.
  4. Language switching works and falls back to `uk`; missing-key test ensures `uk` and `en` have identical key sets.
  5. `npm run api:generate` produces a typed client committed under `frontend/src/api/generated`.
  6. No logo file is drawn or created; missing `assets/brand/` renders the product name text.
- **Tests required:** frontend (i18n key parity, theme tokens), build/lint.
- **Notes:** Decision (Q2): `app.name` (en) = "SC KPI TMS (Team Management System)", `app.shortName` = "SC KPI TMS"; the Ukrainian name is a placeholder value in the `uk` i18n key only (a human finalizes the wording; never hardcoded). Logo source files are in `.claude/resources/img/` and are copied into `assets/brand/` by INC-050; until then the text placeholder is used. Brand color fixed by CLAUDE.md.

#### INC-007 — Frontend app shell: layout, navigation, theme/language switches, error pages
- **Status:** TODO
- **Module(s):** frontend `layout`
- **Depends on:** INC-006
- **Scope:** AppShell with desktop sidebar and mobile header + burger/bottom navigation; nav items registered by feature (placeholders for later features hidden when route absent); notification bell slot; user menu (profile, language, theme, sign out); document title format `<Page> · ІС СР КПІ` via i18n; 404/403/offline pages with logo-mark placeholder and link home; route guard components (`RequireAuth`, `RequirePermission`).
- **Out of scope:** auth wiring (INC-008).
- **Acceptance criteria:**
  1. Layout usable at 375 px (no horizontal scroll, touch targets >= 44 px) in light and dark.
  2. Active nav item uses brand token; no hardcoded hex.
  3. All strings via i18n with uk+en.
  4. `RequirePermission` hides/blocks UI only as a convenience (backend remains authoritative) — documented in code comment and test.
- **Tests required:** frontend (guards, title formatter), manual 375 px check noted in report.
- **Notes:** —

#### INC-008 — Frontend auth: sign-in, forced password change, session handling
- **Status:** TODO
- **Module(s):** frontend `auth`
- **Depends on:** INC-004, INC-007
- **Scope:** sign-in page (logo-full or text placeholder, product name, subtitle `auth.subtitle`); login/logout via generated client with CSRF handling; `useCurrentUser` (from `/auth/me`, includes permissions); forced change-password screen when `mustChangePassword`/`PASSWORD_CHANGE_REQUIRED`; 401 handling redirecting to sign-in; locale from user setting applied after login; profile language switch persists to backend.
- **Out of scope:** account creation.
- **Acceptance criteria:**
  1. Wrong credentials and lockout show localized messages from problem `code`.
  2. User with temporary password cannot reach any route other than change-password.
  3. Sign-in page works at 375 px in both themes; primary button uses brand token.
  4. Permissions from `/me` are exposed via a `usePermission(key)` hook.
- **Tests required:** frontend (login form, redirect logic, hook).
- **Notes:** needs backend endpoint to persist locale (`PATCH /auth/me/locale`) — included in INC-004 scope extension; if missing, report to orchestrator.

#### INC-009 — Files module: FileStorage abstraction and upload/download
- **Status:** DONE
- **Module(s):** backend `files`
- **Depends on:** INC-005
- **Scope:** `FileObject` entity (storageKey, fileName, mimeType, size, uploadedBy); `FileStorage` interface + `LocalFileStorage` (configurable root path, Docker volume ready); endpoints `POST /api/v1/files` (multipart), `GET /api/v1/files/{id}` (streaming, correct content type/disposition); configurable max size and MIME allow-list; public `FileService` for other modules (attach by id); safe key generation (no path traversal); soft-delete (archive) only; audit events.
- **Out of scope:** attachments UI (per feature), S3/MinIO implementation (extension point only), antivirus.
- **Acceptance criteria:**
  1. Upload stores bytes via `FileStorage` and a `FileObject` row; download requires authentication.
  2. Original file names containing `../` cannot escape the storage root (test).
  3. Files over 20 MB (default, configurable) or with a MIME type outside the configurable allow-list are rejected with problem code.
  4. No hard delete endpoint; archived file no longer downloadable by default.
  5. PDFs are served inline-capable (for embedded PDF in KB).
- **Tests required:** unit (key generation), integration (upload/download, limits).
- **Notes:** Decision (Q8): max upload 20 MB (`files.max-size`, configurable). Default allow-list (configurable): PDF; images png/jpeg/gif/webp; text/plain, csv; Office docs (doc/docx, xls/xlsx, ppt/pptx, odt/ods/odp); zip. Executables, scripts, html and svg are rejected; type is checked by content sniffing, not only by extension.

#### INC-010 — Backend seed framework and dev data
- **Status:** DONE
- **Module(s):** backend `shared` (seed runner), `access`
- **Depends on:** INC-005
- **Scope:** idempotent seed runner mechanism (runs once per environment via a `seed_history` table, profile-controlled); dev-profile fake data loader (obviously fake names, no real personal data); hook point for modules to register seeders.
- **Out of scope:** the actual seed roles (INC-013).
- **Acceptance criteria:**
  1. Seeders are idempotent: second start does not duplicate rows.
  2. Dev fake data only loads under the `dev` profile; production profile never loads it (test).
  3. No real personal data in repo.
- **Tests required:** integration.
- **Notes:** —

#### INC-011 — Access: effective permissions in session and permission-denied contract (hardening)
- **Status:** TODO
- **Module(s):** backend `access`
- **Depends on:** INC-005, INC-010
- **Scope:** `GET /api/v1/permissions` (catalog, grouped as §5.2, with i18n labels uk/en); `GET /api/v1/users/{id}/permissions` (requires `roles.assign` or self); verification that a permission change takes effect on the next request for active sessions.
- **Out of scope:** role CRUD (INC-012).
- **Acceptance criteria:**
  1. Catalog endpoint returns all keys with group and localized labels.
  2. Revoking a role from a signed-in user removes the permission on their next request without re-login (integration test).
- **Tests required:** integration.
- **Notes:** small slice; may be merged with INC-012 by the orchestrator if review load allows.

#### INC-012 — Role builder API and UI
- **Status:** TODO
- **Module(s):** backend `access` + frontend `roles`
- **Depends on:** INC-011, INC-008
- **Scope:** Role CRUD API (`roles.manage`): create, rename, edit permission set, archive (soft); assign/revoke roles to users (`roles.assign`); protect system Admin role; UI: roles list, role editor with grouped permission checkboxes, user-role assignment panel (user picker uses members directory once available; until then a simple user list endpoint).
- **Out of scope:** scoping roles to org units (`scopeOrgUnitId` v2).
- **Acceptance criteria:**
  1. Without `roles.manage` all role-mutating endpoints return 403; without `roles.assign`, assignment endpoints return 403.
  2. Admin role cannot be edited, archived or removed from the primary Admin account via API (403/409).
  3. A role in use can be archived; assigned users lose its permissions; audit events record before/after permission sets.
  4. UI works at 375 px; all strings uk+en.
- **Tests required:** unit, integration (permission matrix), frontend (permission editor).
- **Notes:** —

#### INC-013 — Seed template roles
- **Status:** TODO
- **Module(s):** backend `access`
- **Depends on:** INC-012
- **Scope:** seed roles Head, Deputy Head, Head of Department, Head of Division, Member, HR, Advisor with sensible permission sets (§5.2): all authenticated base permissions (`*.view`) in every role; `voting.cast` NOT included in MVP behaviour but defined in seed for Head, Deputy Head, Head of Department per §5.2 note `[v2 usage]`; Advisor = view + comment, no `resolutions.create`, no `voting.cast`; HR has `members.create/edit/archive`, `members.activity.set`; seeds editable/deletable (not `isSystem`).
- **Out of scope:** meetings/polls permissions' behaviour.
- **Acceptance criteria:**
  1. On a fresh DB all 7 roles exist, are non-system, and can be edited/archived.
  2. Advisor lacks `resolutions.create` and `voting.cast`; HR has `members.activity.set`.
  3. Seeding twice does not duplicate.
- **Tests required:** integration.
- **Notes:** permission mapping to be reviewed by a human; recorded in README of seeds.

#### INC-014 — Audit log viewing API and object history
- **Status:** TODO
- **Module(s):** backend `audit` + frontend `audit`
- **Depends on:** INC-003, INC-012
- **Scope:** `GET /api/v1/audit` (requires `audit.view`; filters actor, entityType/entityId, action, period; paginated); `GET /api/v1/audit/entities/{type}/{id}` for history on object pages (requires authentication per openness; `[ASSUMPTION]` object history visible to all authenticated users, global log needs `audit.view`); frontend audit page with filters and a reusable `<EntityHistory>` component with before/after diff.
- **Out of scope:** export.
- **Acceptance criteria:**
  1. Global log endpoint returns 403 without `audit.view`.
  2. Filters by person, entity, period work (tests).
  3. `EntityHistory` renders at 375 px and is reusable by other features.
- **Tests required:** integration, frontend.
- **Notes:** Decision (Q10): per-object history visible to all authenticated users; global log requires `audit.view`.

#### INC-015 — Members: MemberProfile and account creation (backend)
- **Status:** TODO
- **Module(s):** backend `members` (calls `identity` and `access` public services)
- **Depends on:** INC-005, INC-009, INC-052
- **Scope:** `MemberProfile` (lastName, firstName, middleName?, facultyId (dictionary, INC-052), studyGroup, telegram, joinedAt, activityLevel ref, avatar fileId, bio) 1:1 with User; required fields validated (§4); `POST /api/v1/members` (`members.create`) creates User with temporary password (`mustChangePassword=true`) + profile + optional initial role assignment (needs `roles.assign` for the role part); `GET /api/v1/members/{id}`, `PUT` (`members.edit`), self-edit of own bio/avatar/telegram; list endpoint basic (filters in INC-021); `POST .../archive` and `/restore` (`members.archive`) archives profile and blocks sign-in via `identity` while keeping all attribution; temporary password shown once in response, never stored in plaintext/audit; password reset by authorized user.
- **Out of scope:** directory filters (INC-021), org memberships (INC-018), Handover suggestion before archiving `[v2]` (extension point: `MemberArchiving` event allows a future handover hook to veto/prompt).
- **Acceptance criteria:**
  1. Creating a member with a missing required field returns validation problem details listing fields.
  2. Without `members.create` creation returns 403; users with `members.view` base access can read profiles.
  3. Archiving blocks sign-in immediately (existing sessions invalidated) and the member remains referenced by historical records; no hard delete.
  4. Temporary password is not present in audit rows or logs.
  5. Create/edit/archive/restore emit audit events; `MemberCreated/Updated/Archived` domain events published.
- **Tests required:** unit (validation), integration (permission matrix, archive blocks sign-in).
- **Notes:** Decision (Q5): faculty/institute is a managed dictionary (INC-052); `MemberProfile.facultyId` references it; a member cannot be saved with an unknown or archived faculty.

#### INC-016 — Members frontend: directory (basic), profile, create/edit, archive
- **Status:** TODO
- **Module(s):** frontend `members`
- **Depends on:** INC-015, INC-008
- **Scope:** members list (cards on mobile, table on desktop) with text search; profile page (data, bio, avatar via files, contact Telegram link); create-member form (shows temporary password once with copy button); edit; archive/restore with confirmation; own-profile editing and language setting.
- **Out of scope:** filters by org/position (INC-021), positions/memberships display (INC-021).
- **Acceptance criteria:**
  1. Buttons for create/edit/archive appear only with the corresponding permission, and backend 403 is handled gracefully.
  2. Forms validate required fields client-side and display server problem details.
  3. 375 px / light+dark verified; uk+en complete.
- **Tests required:** frontend (form validation, permission-gated actions).
- **Notes:** —

#### INC-017 — Org: OrgUnit tree (backend)
- **Status:** TODO
- **Module(s):** backend `org`
- **Depends on:** INC-005
- **Scope:** `OrgUnit` (name, type LEADERSHIP|DEPARTMENT|DIVISION|WORKING_GROUP, parentId, description, archivedAt); invariants: DIVISION parent must be a DEPARTMENT, WORKING_GROUP has no hierarchy parent requirement (outside the hierarchy), only one LEADERSHIP root, no cycles; create/rename/move/archive/restore (`org.manage`); `GET` tree and node endpoints (`org.view` base); archiving a unit with active children or active memberships is refused or requires explicit flag `[ASSUMPTION]`; events and audit.
- **Out of scope:** memberships (INC-018), positions (INC-019).
- **Acceptance criteria:**
  1. Type/parent invariants enforced and unit-tested (e.g. division under division rejected).
  2. Cycle creation rejected.
  3. Archiving is soft; archived units hidden from default tree, restorable.
  4. All mutations require `org.manage`; reads need authentication only.
- **Tests required:** unit (invariants), integration.
- **Notes:** Working groups: "disbanded" == archived (§2.2).

#### INC-018 — Org: Membership (user <-> OrgUnit, M:N)
- **Status:** TODO
- **Module(s):** backend `org`
- **Depends on:** INC-017, INC-015
- **Scope:** `Membership` (userId, orgUnitId, since, until); add/end/move a person between units (`org.manage`); a person may belong to several units; list members of a unit and units of a person; reacts to `MemberArchived` by ending active memberships; public `OrgQueryService` for other modules (unit ids of a user, member ids of unit).
- **Out of scope:** UI (INC-020).
- **Acceptance criteria:**
  1. A user may hold simultaneous active memberships in multiple departments (§2.2).
  2. Ending membership sets `until` and keeps history; moves create end+start records.
  3. Archived members' memberships end automatically with audit events.
  4. Mutations need `org.manage`.
- **Tests required:** unit, integration.
- **Notes:** —

#### INC-019 — Org: Position, Appointment, role binding
- **Status:** TODO
- **Module(s):** backend `org` (calls `access` public service)
- **Depends on:** INC-018, INC-005
- **Scope:** `Position` (title, orgUnitId, defaultRoleIds[], isHead); `Appointment` (positionId, userId, startDate, endDate) with history ("who held it before"); `positions.manage` for position CRUD, `positions.appoint` for appoint/end; appointing grants `defaultRoleIds` via `AccessService`, ending the appointment revokes them (only roles granted by that appointment; if the user has the same role from another source it stays); head appointment sets unit head; a position may be vacant; position exists independently of person.
- **Out of scope:** Handover `[v2]`.
- **Acceptance criteria:**
  1. Appointing grants bound roles; ending (or archiving the member) revokes them (integration test on effective permissions).
  2. Position history lists previous holders with dates; ended appointments are never deleted.
  3. Only one active appointment per user per position; multiple holders only when position allows `[ASSUMPTION: one active holder, head positions]`.
  4. `positions.manage` / `positions.appoint` enforced separately; APPOINT audit events emitted.
- **Tests required:** unit (role grant/revoke logic), integration.
- **Notes:** `[ASSUMPTION]` single holder per position; configurable by capacity field.

#### INC-020 — Org frontend: structure editor, positions, appointments
- **Status:** TODO
- **Module(s):** frontend `org`
- **Depends on:** INC-019, INC-016
- **Scope:** tree view of the structure (collapsible, mobile-friendly), unit page (description, head, members, positions with holder and history); create/rename/archive/move unit; add/remove members; position CRUD; appoint/end appointment dialogs; working groups listed separately.
- **Out of scope:** drag-and-drop (nice-to-have; not required).
- **Acceptance criteria:**
  1. Manage actions visible only with `org.manage` / `positions.*`; read-only view for everyone.
  2. Position history visible on the position page.
  3. 375 px, light/dark, uk+en.
- **Tests required:** frontend.
- **Notes:** —

#### INC-021 — Members directory filters and extended profile
- **Status:** TODO
- **Module(s):** backend `members` + frontend `members`
- **Depends on:** INC-019, INC-016, INC-052
- **Scope:** directory filters by department, division, faculty, position, activity level, status + search by name/telegram (backend uses `OrgQueryService`, no direct access to org tables); profile shows positions (current/past), memberships, and slots for active tasks/resolutions (filled in INC-037) and statistics placeholder; filter UI as collapsible drawer on mobile.
- **Out of scope:** statistics beyond counts (full stats `[v2]`).
- **Acceptance criteria:**
  1. Each filter narrows results correctly (integration test with fake data); combinations are AND.
  2. Pagination and sort stable.
  3. Module boundary: `members` imports only `org` public API (Modulith verification passes).
- **Tests required:** integration, frontend.
- **Notes:** —

#### INC-022 — Activity level (HR) with history
- **Status:** TODO
- **Module(s):** backend `members` + frontend `members`
- **Depends on:** INC-021
- **Scope:** `ActivityLevelDefinition` (code, label uk/en, order) as editable reference data seeded with a placeholder scale (High/Medium/Low/Inactive) `[OPEN-Q3]`; `ActivityLevelChange` history (member, from, to, comment?, changedBy, at); `PUT /members/{id}/activity-level` requires `members.activity.set`; filter by level in the directory; profile shows current level and change history.
- **Out of scope:** distribution statistics `[v2]`.
- **Acceptance criteria:**
  1. Setting a level without `members.activity.set` -> 403; with it -> history row + audit event.
  2. Scale changes require only data edits, not code changes (definition table).
  3. History is append-only.
- **Tests required:** unit, integration, frontend.
- **Notes:** `[OPEN-Q3]` scale not decided; do not hardcode.

#### INC-023 — Onboarding gate: first-login checklist for HR admission process
- **Status:** TODO
- **Module(s):** frontend `members`
- **Depends on:** INC-015, INC-016
- **Scope:** post-creation helper in the create-member flow supporting §5.1 steps 3–4 (profile, department(s), role in one wizard calling existing APIs); no new backend.
- **Out of scope:** admission form inside CRM `[LATER]`.
- **Acceptance criteria:**
  1. HR can create a member, add to departments and assign a role in a single guided flow; each step respects its own permission (steps are skipped/disabled without it).
  2. Failure of a later step leaves earlier steps intact and shows what remains.
- **Tests required:** frontend.
- **Notes:** optional slice; orchestrator may defer.

#### INC-024 — Tasks: Space with configurable statuses and custom fields (backend)
- **Status:** TODO
- **Module(s):** backend `tasks`
- **Depends on:** INC-017
- **Scope:** `Space` (name, orgUnitId?, general flag); ordered `SpaceStatus` with category TODO|IN_PROGRESS|DONE|CANCELED; `CustomFieldDefinition` (text, number, date, select with options, person); CRUD (`spaces.manage`); new space gets default statuses; cannot delete/archive a status used by tasks without mapping to another; space archive (soft); reads need `tasks.view`.
- **Out of scope:** tasks themselves (INC-025).
- **Acceptance criteria:**
  1. Each space has >= 1 status per required category set (at least one TODO and one DONE category) enforced.
  2. Status reorder persists order; categories restricted to the 4 enum values.
  3. Custom field types limited to the 5 in §5.3; select requires options.
  4. `spaces.manage` enforced; audit events.
- **Tests required:** unit (status invariants), integration.
- **Notes:** —

#### INC-025 — Tasks: Task CRUD, labels, related tasks, archive
- **Status:** TODO
- **Module(s):** backend `tasks`
- **Depends on:** INC-024, INC-009
- **Scope:** `Task` (spaceId, title, rich-text description JSON, statusId, priority, labels[], dueDate, customFieldValues validated against the space definition, attachments[] via `files`, relatedTaskIds[] (no hierarchy), archivedAt); create (`tasks.create`), edit (task author or `Admin` only, per decision Q7), archive (`tasks.archive`), status change; list with filters (space, status, labels, due date) and pagination; change history through audit events.
- **Out of scope:** resolution filters (INC-032+), comments (INC-027), UI.
- **Acceptance criteria:**
  1. Status must belong to the task's space; custom field values validated by type.
  2. No parent/child task relation exists; related links are symmetric references only.
  3. Only the task author or `Admin` can edit a task; any other user gets 403. The `tasks.edit.any` key stays in the permission catalog (CONTEXT.md §5.2) but is not granted by default seed roles and is not part of this rule; the rule lives in a single `TaskEditPolicy` class so a human can change it.
  4. Archive/restore requires `tasks.archive`; archived tasks hidden by default.
  5. `TaskCreated/Updated/Archived` events and audit events emitted with before/after.
- **Tests required:** unit, integration.
- **Notes:** Decision (Q7): author-or-Admin edit rule.

#### INC-026 — Tasks frontend: spaces management and task list view
- **Status:** TODO
- **Module(s):** frontend `tasks`
- **Depends on:** INC-025, INC-008
- **Scope:** spaces list/settings (statuses editor, custom fields editor, gated by `spaces.manage`); task list view with filters (space, status, due date, labels) and sorting; task create/edit form (title, rich-text description via shared minimal editor component, priority, labels, due date, custom fields); task detail page skeleton (comments, resolutions, history tabs wired later).
- **Out of scope:** kanban (INC-028), comments (INC-027).
- **Acceptance criteria:**
  1. List usable at 375 px as cards; all filters in a drawer.
  2. Custom fields render by type and validate.
  3. Permission-gated actions; uk+en.
- **Tests required:** frontend.
- **Notes:** Rich-text editor component is built in INC-038; here use a shared lightweight TipTap-based description field created in this increment and reused later `[extract to shared]`. Orchestrator may reorder INC-038's editor core ahead.

#### INC-027 — Tasks: comments with @mentions and attachments
- **Status:** TODO
- **Module(s):** backend `tasks` + frontend `tasks`
- **Depends on:** INC-025, INC-026
- **Scope:** `Comment` (targetType, targetId, authorId, rich-text body, mentions[]); MVP target = `TASK` and `RESOLUTION` (type is generic for later targets); create/edit-own/archive-own (moderators `tasks.edit.any`); mention extraction (user ids) publishing `UserMentioned` events; attachments via `files`; UI thread with mention picker.
- **Out of scope:** KB comments.
- **Acceptance criteria:**
  1. Advisor role (view + comment) can comment; users without any task permission beyond view can comment `[ASSUMPTION: commenting needs authentication only]`.
  2. Mentions stored as user ids; mentioning an archived user rejected.
  3. Edit keeps history via audit events; deletes are archive-only.
- **Tests required:** unit, integration, frontend.
- **Notes:** commenting needs authentication only (no comment permission in §5.2); edit/archive of a comment only by its own author or Admin.

#### INC-028 — Tasks: kanban view
- **Status:** TODO
- **Module(s):** frontend `tasks` (backend status-change endpoint already exists)
- **Depends on:** INC-026
- **Scope:** kanban board per space (columns = statuses, ordered); move cards via drag-and-drop on desktop and a "move to" menu on touch; WIP-free; card shows title, due, labels, overdue highlight.
- **Out of scope:** calendar `[v2]`.
- **Acceptance criteria:**
  1. Moving a card calls the status-change API and updates optimistically; failure rolls back with message.
  2. Board is operable at 375 px without drag-and-drop.
  3. Columns follow the space's configured statuses.
- **Tests required:** frontend.
- **Notes:** —

#### INC-029 — Tasks: saved filters and quick views
- **Status:** TODO
- **Module(s):** backend `tasks` + frontend `tasks`
- **Depends on:** INC-025, INC-032
- **Scope:** filters by executor, resolution author, resolution state, labels; built-in saved filters "My resolutions", "Issued by me", "Overdue"; user-defined saved filters persisted per user.
- **Out of scope:** shared team filters.
- **Acceptance criteria:**
  1. The three built-ins return correct results (integration tests with seed data).
  2. "Overdue" = due date passed and state not ACCEPTED/CANCELED (§5.3).
  3. Saved filters are private to the user.
- **Tests required:** integration, frontend.
- **Notes:** depends on resolutions data; schedule after INC-032 (dependency declared).

#### INC-030 — Tasks: attachments UI and file previews
- **Status:** TODO
- **Module(s):** frontend `tasks`
- **Depends on:** INC-025, INC-009
- **Scope:** upload/list/download attachments on tasks and comments; image thumbnail; progress and error states.
- **Acceptance criteria:**
  1. Rejected files show the localized problem message.
  2. Works on mobile (camera/file picker).
- **Out of scope:** previews of office docs.
- **Tests required:** frontend.
- **Notes:** may be merged into INC-026/027 by the orchestrator.

#### INC-031 — Tasks: "all resolutions accepted" suggestion hook (task side)
- **Status:** TODO
- **Module(s):** backend `tasks`
- **Depends on:** INC-025, INC-032
- **Scope:** `TaskService.suggestDone(taskId)` computed field `suggestDone=true` on the task DTO when the task has >= 1 resolution and all non-canceled resolutions are ACCEPTED and the task is not in a DONE-category status; no automatic status change (§5.3).
- **Acceptance criteria:**
  1. Flag appears only under the rule above (unit tests with state combinations).
  2. Task status never changes automatically.
- **Out of scope:** UI banner (INC-036).
- **Tests required:** unit.
- **Notes:** requires the Resolution entity from INC-032; schedule after it.

#### INC-032 — Resolutions: domain model and lifecycle state machine
- **Status:** TODO
- **Module(s):** backend `tasks`
- **Depends on:** INC-025
- **Scope:** `Resolution` entity (taskId, authorId, executorId, coExecutorIds[], instruction, dueDate, parentResolutionId?, state, completionNote?, history[]); `ResolutionState`: NEW, IN_PROGRESS, SUBMITTED, REWORK, ACCEPTED, REDIRECTED, CANCELED; pure domain state machine with transitions exactly per §5.3; history entries (who, from, to, comment, at); migration; no API yet beyond internal service.
- **Out of scope:** REST endpoints (INC-033), redirect (INC-034).
- **Acceptance criteria:**
  0. Cancel cascade (decision Q6): canceling a resolution that has child resolutions (REDIRECTED chain) cancels all non-final descendants recursively and atomically; ACCEPTED/CANCELED descendants are left untouched; each canceled descendant gets a history entry referencing the parent cancel. Unit tests cover a 4-level chain, mixed final/non-final descendants and atomic rollback.
  1. Allowed transitions only: NEW->IN_PROGRESS (executor accepts); IN_PROGRESS->SUBMITTED (completion note mandatory); SUBMITTED->ACCEPTED (author); SUBMITTED->REWORK (author, mandatory comment); REWORK->IN_PROGRESS; IN_PROGRESS->REDIRECTED; any non-final -> CANCELED (author). ACCEPTED, CANCELED are final; every other transition throws a domain error with stable code.
  2. Unit tests cover every allowed and a matrix of forbidden transitions, actor checks (only executor can accept/submit/redirect, only author can accept/rework/cancel).
  3. Completion note and rework comment rejected when blank.
  4. Overdue = dueDate in the past and state not in {ACCEPTED, CANCELED} (pure function, tested with `Clock`).
  5. Every transition appends a history entry.
- **Tests required:** unit (exhaustive state machine), integration (persistence + history).
- **Notes:** REWORK is a distinct state that moves to IN_PROGRESS (executor resumes).

#### INC-033 — Resolutions: REST API (issue, accept, submit, rework, accept result, cancel)
- **Status:** TODO
- **Module(s):** backend `tasks`
- **Depends on:** INC-032, INC-005
- **Scope:** `POST /tasks/{id}/resolutions` (`resolutions.create`; any executor allowed including peers/superiors; executor + co-executors must be ACTIVE members; due date >= today); transitions endpoints; list by task, by executor/author/state, "mine"; resolution detail with history; domain events `ResolutionIssued/Accepted/Submitted/Reworked/Closed/Canceled` + audit.
- **Out of scope:** redirect (INC-034), UI.
- **Acceptance criteria:**
  1. User without `resolutions.create` gets 403 on issue; with it can issue top-down, horizontally and bottom-up (no hierarchy check) — integration test for each direction.
  2. A task can have multiple resolutions.
  3. Non-executor cannot accept/submit; non-author cannot accept/rework/cancel (403 with code).
  4. Rework requires a comment; submit requires a completion note.
  5. Events are published for notifications (INC-043) with ids of author/executor/co-executors.
- **Tests required:** integration (permission and actor matrix), unit.
- **Notes:** Advisor cannot issue (seed, §5.2).

#### INC-034 — Resolutions: redirect and chain
- **Status:** TODO
- **Module(s):** backend `tasks`
- **Depends on:** INC-033
- **Scope:** `POST /resolutions/{id}/redirect` (`resolutions.redirect`, executor of parent only, parent must be IN_PROGRESS): creates child resolution (parentResolutionId, new executor, instruction, due date <= parent's `[ASSUMPTION: not enforced, warning only]`), parent -> REDIRECTED atomically; `GET /resolutions/{id}/chain` returns the whole tree rooted at the first resolution, visible to everyone with `tasks.view`; when a child becomes ACCEPTED an event `ChildResolutionAccepted` notifies the parent executor, parent is NOT auto-submitted (§5.3 `[ASSUMPTION]`).
- **Out of scope:** UI.
- **Acceptance criteria:**
  1. Redirect creates exactly one child and moves parent to REDIRECTED in a single transaction (rollback test).
  2. Chain endpoint returns tree Head -> Deputy -> Head of Dept -> Member for a 4-level fixture.
  3. Redirect by non-executor or without `resolutions.redirect` -> 403; redirect to self rejected; redirect cycles (to someone already in the chain) rejected `[ASSUMPTION]`.
  4. Accepting a child does not change parent state.
  5. Canceling a REDIRECTED resolution (by its author) cascade-cancels all non-final child resolutions down the chain in one transaction and publishes events for the children's executors (decision Q6); tested through the API.
- **Tests required:** unit, integration.
- **Notes:** cascade rule is implemented in INC-032 domain logic; this increment exposes it via API and events.

#### INC-035 — Resolutions frontend: issue, view, act
- **Status:** TODO
- **Module(s):** frontend `resolutions` (within tasks area)
- **Depends on:** INC-033, INC-027
- **Scope:** on task detail: "Task X — resolution to Y" list (author, date, due, state badge); issue-resolution dialog (executor picker, co-executors, instruction, due date); executor actions (accept, submit with completion note, redirect), author actions (accept, rework with comment, cancel); mobile-first sheet dialogs; overdue highlighting; resolution history timeline.
- **Out of scope:** chain tree (INC-036).
- **Acceptance criteria:**
  1. Only valid actions for current user/state are offered (matches state machine) and server rejection is surfaced.
  2. Completion-note and rework comment fields required in UI.
  3. Complete issue -> accept -> submit -> accept scenario works at 375 px.
  4. State badges use status colors (not brand blue); uk+en.
- **Tests required:** frontend (action availability logic), component tests.
- **Notes:** —

#### INC-036 — Resolutions frontend: chain tree and "task done" suggestion
- **Status:** TODO
- **Module(s):** frontend `resolutions`
- **Depends on:** INC-034, INC-031, INC-035
- **Scope:** collapsible tree of the redirect chain visible to the root author; banner "all resolutions accepted — move task to Done?" using `suggestDone`, with one-click move to a DONE-category status (user choice among DONE statuses).
- **Acceptance criteria:**
  1. Tree renders nested levels legibly at 375 px.
  2. Banner appears only when `suggestDone` is true and moves task only on confirmation.
- **Out of scope:** automatic moves.
- **Tests required:** frontend.
- **Notes:** —

#### INC-037 — Resolutions: profile and personal lists integration
- **Status:** TODO
- **Module(s):** backend `tasks` + frontend `members`
- **Depends on:** INC-033, INC-021
- **Scope:** `GET /members/{id}/resolutions?active=true` served by `tasks` public API; member profile shows active tasks/resolutions and simple counts (active, overdue, accepted); "My resolutions" and "Issued by me" pages with state filters.
- **Out of scope:** statistics dashboards `[v2]`.
- **Acceptance criteria:**
  1. Counts match fixtures (overdue computed per §5.3 rule).
  2. Profile of another member is visible to all authenticated users (openness).
  3. `members` uses only the `tasks` public interface.
- **Tests required:** integration, frontend.
- **Notes:** —

#### INC-038 — Knowledge: Article CRUD, hierarchy, tags (backend)
- **Status:** TODO
- **Module(s):** backend `knowledge`
- **Depends on:** INC-005, INC-009
- **Scope:** `Article` (title, rich-text JSON content, parentArticleId, tags[], attachments[] via files, version, restricted flag for `kb.restricted.view`, archivedAt); create/edit (`kb.edit`), archive/restore (`kb.archive`), view (`kb.view`; restricted articles need `kb.restricted.view`, unused by default); page tree and children endpoints; move article; no cycles; tag list/filter; article links/mentions (articles, tasks, people) stored as references in content JSON; plain-text extraction for search.
- **Out of scope:** versions (INC-039), editor UI (INC-040).
- **Acceptance criteria:**
  1. Page hierarchy without cycles; archiving a parent archives or blocks children (rule recorded `[ASSUMPTION: refuse while active children exist]`).
  2. Restricted articles hidden from users without `kb.restricted.view` (including in lists/search); default articles visible to all authenticated users.
  3. Permission checks `kb.edit` / `kb.archive` enforced; events/audit emitted.
- **Tests required:** unit, integration.
- **Notes:** —

#### INC-039 — Knowledge: versioning
- **Status:** TODO
- **Module(s):** backend `knowledge`
- **Depends on:** INC-038
- **Scope:** `ArticleVersion` snapshot on every save (content, title, editor, at, version number); list versions, read a version, restore (creates a new version, never rewrites history); optimistic locking on concurrent edits (409 problem code).
- **Acceptance criteria:**
  1. Each save creates exactly one version with incrementing number.
  2. Restore produces a new latest version identical in content to the chosen one; old versions remain.
  3. Concurrent save with stale version -> 409.
- **Out of scope:** diff UI beyond basic.
- **Tests required:** unit, integration.
- **Notes:** —

#### INC-040 — Knowledge frontend: TipTap editor and article pages
- **Status:** TODO
- **Module(s):** frontend `knowledge`
- **Depends on:** INC-039, INC-009
- **Scope:** TipTap editor with blocks required by §5.4: headings, text size, bold/italic/underline/strike, text and background color, bullet/numbered/checklist, quote, table, link, image, file attachment, embedded PDF, divider, code block, callout; slash menu; @mentions (people, articles, tasks); autosave-safe save with version conflict handling; page tree sidebar, breadcrumbs, tags; read mode and edit mode; mobile toolbar.
- **Out of scope:** real-time collaboration.
- **Acceptance criteria:**
  1. Each listed block type can be inserted via toolbar or slash menu and round-trips through save/load (frontend tests on JSON).
  2. Reading an article is comfortable at 375 px; editing usable with the mobile toolbar.
  3. Editor colors come from theme tokens; works in dark theme.
  4. Content stored as TipTap JSON.
- **Tests required:** frontend (schema round trip), component tests.
- **Notes:** the lightweight editor from INC-026 should be replaced by this component; orchestrator may split editor core into a separate increment.

#### INC-041 — Knowledge frontend: version history and restore
- **Status:** TODO
- **Module(s):** frontend `knowledge`
- **Depends on:** INC-040
- **Scope:** version list drawer, preview of a version, restore with confirmation.
- **Acceptance criteria:**
  1. Restore requires `kb.edit`; button hidden otherwise, 403 handled.
  2. Works at 375 px.
- **Out of scope:** text diff.
- **Tests required:** frontend.
- **Notes:** —

#### INC-042 — Knowledge: onboarding lists and "I've read this"
- **Status:** TODO
- **Module(s):** backend `knowledge` + frontend `knowledge`
- **Depends on:** INC-040
- **Scope:** `OnboardingList` (ordered articleIds, optional orgUnitId) managed with `onboarding.manage`; "For newcomers" page showing general list plus lists for the user's departments (via `org` public API); personal read mark per user per article (private, no supervision, `[ASSUMPTION]`); newcomer prompt to open onboarding on first login after account creation.
- **Out of scope:** read tracking visible to others.
- **Acceptance criteria:**
  1. Order is preserved and editable; only `onboarding.manage` can edit.
  2. Read marks are visible only to the owner (test that no endpoint exposes others' marks).
  3. Archived articles disappear from lists without breaking order.
- **Tests required:** integration, frontend.
- **Notes:** —

#### INC-043 — Notifications: core, channel abstraction, in-app delivery (backend)
- **Status:** TODO
- **Module(s):** backend `notifications`
- **Depends on:** INC-033, INC-027
- **Scope:** `Notification` (recipientId, type, targetRef, readAt, payload); `NotificationChannel` interface with `InAppChannel` implementation (extension point for Telegram/email `[LATER]`, not implemented); event listeners creating notifications for: resolution issued to me/co-executor, redirect, submitted for acceptance, accepted, returned for rework, child resolution accepted, @mention; endpoints: list (paginated, unread filter), unread count, mark read, mark all read; notifications for the actor themselves suppressed; poll/handover types reserved in enum only.
- **Out of scope:** due-date reminders (INC-044), UI (INC-045).
- **Acceptance criteria:**
  1. Each event type in scope creates notifications for the correct recipients only (integration tests per type).
  2. A user can read/mark only their own notifications (403/404 otherwise).
  3. Adding a new channel requires no change to business modules (verified by design: listeners depend on the `NotificationChannel` abstraction).
  4. Notification texts rendered from i18n keys + payload, stored locale-independent.
- **Tests required:** unit, integration.
- **Notes:** —

#### INC-044 — Notifications: due-date approaching/passed scheduler
- **Status:** TODO
- **Module(s):** backend `notifications`
- **Depends on:** INC-043
- **Scope:** scheduled job (configurable lead time, default 24 h, Europe/Kyiv-aware day boundaries) via `tasks` public API listing active resolutions; at-most-once notifications per resolution per threshold (idempotency table); "overdue" notification when passing due date.
- **Acceptance criteria:**
  1. Job is idempotent: running twice does not duplicate notifications.
  2. Only resolutions not in ACCEPTED/CANCELED are considered.
  3. Time uses the injected `Clock` (testable).
- **Out of scope:** poll reminders `[v2]`.
- **Tests required:** unit, integration.
- **Notes:** —

#### INC-045 — Notifications frontend: bell and page
- **Status:** TODO
- **Module(s):** frontend `notifications`
- **Depends on:** INC-043, INC-007
- **Scope:** header bell with unread counter (polling interval via TanStack Query), dropdown of latest, notifications page, mark read/all, deep link to target.
- **Acceptance criteria:**
  1. Counter updates after marking read without full reload.
  2. Deep links open resolution/task/comment target.
  3. 375 px, uk+en.
- **Out of scope:** push/websocket.
- **Tests required:** frontend.
- **Notes:** —

#### INC-046 — Search: index and API
- **Status:** TODO
- **Module(s):** backend `search`
- **Depends on:** INC-021, INC-025, INC-033, INC-038, INC-017
- **Scope:** ADR-0002 (search module owns a denormalized `search_document` table fed by domain events from members, org, tasks, resolutions, knowledge; PostgreSQL FTS with `simple`/configured dictionaries plus `pg_trgm`; Flyway enabling extension); `GET /api/v1/search?q=` returning results grouped by type (tasks, resolutions, articles incl. body text, people, org units); partial-match tolerance; Ukrainian and English; respects visibility (restricted articles excluded without `kb.restricted.view`; archived excluded); initial reindex command/admin endpoint.
- **Out of scope:** meetings/polls `[v2]`.
- **Acceptance criteria:**
  1. Query "резол" finds a resolution titled "Резолюція ..." (partial); an English fixture also matches.
  2. Article body text is searchable; restricted article not returned to a user lacking permission.
  3. Updating/archiving an entity updates/removes it from the index (event-driven test).
  4. `search` accesses other modules only through events/public APIs (Modulith verify).
- **Tests required:** integration (Testcontainers with pg_trgm).
- **Notes:** if upstream modules lack events, add them there under their own increments; report to orchestrator.

#### INC-047 — Search frontend: global search bar and results
- **Status:** TODO
- **Module(s):** frontend `search`
- **Depends on:** INC-046, INC-007
- **Scope:** header search with debounce, results page grouped by type with counts, keyboard shortcut on desktop, full-screen search on mobile.
- **Acceptance criteria:**
  1. Results grouped by type, each linking to its page.
  2. Empty/error/loading states localized; 375 px.
- **Out of scope:** advanced filters.
- **Tests required:** frontend.
- **Notes:** —

#### INC-048 — "My resolutions" mini-dashboard widget
- **Status:** TODO
- **Module(s):** backend `analytics` (thin) + frontend `dashboard`
- **Depends on:** INC-037, INC-045
- **Scope:** home page widget: my active, overdue, awaiting my acceptance counts and top upcoming items; backend `GET /api/v1/dashboard/me` (permission `dashboard.view` for the widget `[ASSUMPTION: base roles get it in seed]`) built on `tasks` public API; extension point for the v2 shared dashboard.
- **Out of scope:** shared statistics, periods `[v2]`.
- **Acceptance criteria:**
  1. Counts equal the "My resolutions" lists (consistency test).
  2. Widget responsive at 375 px; links open filtered lists.
  3. No cross-user statistics exposed.
- **Tests required:** integration, frontend.
- **Notes:** add `dashboard.view` to seed roles (update INC-013 seeds via new migration/seed version, not by editing history).

#### INC-049 — Deployment: Docker Compose, nginx, backups
- **Status:** TODO
- **Module(s):** `deploy/` (+ Dockerfiles at `deploy/`, no files in `src/`)
- **Depends on:** INC-008
- **Scope:** `deploy/docker-compose.yml` (services `backend`, `frontend` (nginx static + reverse proxy `/api`, same-domain cookie), `postgres:16`, `backup`); backend Dockerfile (multi-stage Maven build, Java 21), frontend Dockerfile (Node build -> nginx; `frontend/dist` not copied into `src/main/resources/static`); `nginx.conf` (HTTPS-ready, gzip, security headers, SPA fallback); volumes for DB and file storage; `deploy/.env.example` with placeholders only; daily backup script for DB + file volume with retention; restore instructions; healthchecks; README deployment section; ADR-0003 deployment topology.
- **Out of scope:** TLS certificate issuance (documented), CI/CD.
- **Acceptance criteria:**
  1. `docker compose -f deploy/docker-compose.yml up --build` starts all services and the SPA can sign in through nginx.
  2. No secrets committed; `.env` is git-ignored; `.env.example` has placeholders.
  3. Backup script produces timestamped DB dump and file archive; restore procedure documented and tested once.
  4. Session cookie is `Secure` under the prod profile.
- **Tests required:** smoke script (health endpoint via nginx).
- **Notes:** All host-specific values (domain, TLS cert paths, backup target, server user) are parameters in `deploy/.env.example` and nginx templates; no real values committed. Server/domain/TLS ownership is unresolved (OPEN-Q9) and is the only item needing a human before a real deployment.

#### INC-050 — Frontend PWA-free polish: brand assets integration and a11y pass
- **Status:** TODO
- **Module(s):** frontend `layout`
- **Depends on:** INC-007, INC-047
- **Scope:** wire real files from `assets/brand/` when they exist (build step copies/imports; favicon, title, logo variants per §6.1 placement table); until present keep text placeholders; contrast/focus audit of light+dark; empty/error states across features.
- **Out of scope:** PWA manifest/service worker `[v2]`.
- **Acceptance criteria:**
  0. `assets/brand/` contains the files copied from `.claude/resources/img/` plus `README.md` (what each file is, where used, color `#0700CD`); missing variants are listed there as missing.
  1. With assets absent the build succeeds and the placeholder shows; with assets present, logo placements in §6.1 render with correct light/dark variants.
  2. Dark theme: no shade-6 brand text/icons (grep/test check).
  3. All visible strings have uk+en keys (parity test).
- **Tests required:** frontend, manual audit noted in report.
- **Notes:** Decision (Q1): source files are in `.claude/resources/img/` (currently two PNGs: logo mark and full logo, blue). Implementator copies them into `assets/brand/` (per the README mapping; no SVG, light-background variant or PWA icons exist, so those stay placeholders and are reported missing; never generate or recolor) and writes `assets/brand/README.md`. If a hook blocks writing to `assets/brand/`, the implementator must report it and not work around it.

#### INC-051 — End-to-end scenarios (Playwright)
- **Status:** TODO
- **Module(s):** `frontend/e2e`
- **Depends on:** INC-049, INC-048, INC-042
- **Scope:** Playwright suite on the compose stack with seeded fake data covering: sign-in with forced password change; HR creates member + department + role; issue resolution -> redirect -> submit -> rework -> accept; read an article and create a new version; search; notification bell; all run also at 375 px viewport.
- **Out of scope:** load testing.
- **Acceptance criteria:**
  1. All listed scenarios pass headless in one command documented in README.
  2. Tests use only fake data.
  3. At least the resolution scenario runs in a mobile viewport project.
- **Tests required:** e2e.
- **Notes:** —

#### INC-052 — Faculty/institute dictionary (managed reference data)
- **Status:** TODO
- **Module(s):** backend `members` + frontend `members` (admin settings page)
- **Depends on:** INC-005, INC-010
- **Scope:** `Faculty` entity (code, name uk, name en, archivedAt) with CRUD and archive/restore; mutations need `settings.manage` (Admin has it; grantable via roles), read for any authenticated user; migration; seed hook with a small generic placeholder list (real list supplied by humans); admin UI page; used by INC-015 (`MemberProfile.facultyId`) and the directory filter in INC-021.
- **Out of scope:** faculty hierarchy, faculty councils `[LATER]`.
- **Acceptance criteria:**
  1. Create/edit/archive/restore require `settings.manage`; others get 403; list readable by all authenticated users.
  2. Archiving a faculty in use is soft and keeps existing profiles intact; archived faculties are not selectable for new or edited profiles.
  3. Names are unique among active faculties; uk and en names both required.
  4. Audit events emitted; UI works at 375 px with uk+en.
- **Tests required:** unit, integration, frontend.
- **Notes:** decision Q5. Numbered last but must be scheduled before INC-015.

### Open questions
- **OPEN-Q3** (INC-022): activity level scale (tiers vs numeric) with HR (CONTEXT.md §10 item 3). Plan uses editable reference data with a placeholder scale.
- **OPEN-Q9** (INC-049): who owns servers, domain and TLS at the university (CONTEXT.md §10 item 5), and the backup target. INC-049 is fully parametrized; this is the only item requiring a human before a real deployment.
- **Ukrainian product name** (INC-006): placeholder in the `uk` i18n key; a human provides the final wording.
- **Missing brand variants** (INC-050): no SVG, dark-background logo variant or favicon/PWA icons in `.claude/resources/img/`; to be supplied by a human if wanted.
- **Resolved by the user (2026-10-07):** Q1 logo source in `.claude/resources/img/`; Q2 product name; Q4 individual passwords + brute-force protection with configurable defaults; Q5 faculty is a managed dictionary; Q6 cascade cancel; Q7 author-or-Admin task edit; Q8 20 MB + allow-list; Q10 object history open to all, global log needs `audit.view`.
- **Not planned (requires human decision, outside MVP):** §10 items 1, 2, 4, 6 (events entity, secret ballots, faculty councils, public reporting); `settings.manage` permission has no MVP feature and is only defined in the catalog; §5.11 administrator hard delete is not planned in MVP (soft delete only) `[ASSUMPTION]`.
