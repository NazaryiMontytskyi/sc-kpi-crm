# PLAN.md

## Plan: MVP
Source: CONTEXT.md §9 (MVP), §5.1–5.4, 5.8–5.11, §6, §8; CLAUDE.md "Project setup" (overrides §6.1, §8.1, §8.3) · Created: 2026-10-07

### Overview
Delivers the `[MVP]` stage only: accounts, team records/org, role builder, task tracker with resolutions, knowledge base with onboarding, global search, in-app notifications, audit log, "My resolutions" widget, uk/en, Docker Compose deployment. Meetings, voting, Handover, shared dashboard, calendar view and PWA are `[v2]` and are NOT planned (extension points are noted where relevant).
Phases (by dependency, not strictly by number): A Foundation (001-010) -> B Access/Audit (011-014) -> C Members/Org (052 faculty dictionary backend first, then 015-023; 053 faculty admin page) -> D Tasks core (024-028, 030) -> E Resolutions (032-037, then 029 saved filters and 031 done-suggestion, which depend on INC-032) -> F Knowledge (038-042) -> G Notifications, Search, Widget (043-048) -> H Deployment, brand assets, E2E (049-051). INC-052 (faculty dictionary, backend) is numbered last but scheduled before INC-015; INC-053 (its admin page, frontend) follows INC-052 and INC-008.
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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
- **Status:** DONE
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

#### INC-052 — Faculty/institute dictionary (backend)
- **Status:** DONE
- **Module(s):** backend `members`
- **Depends on:** INC-005, INC-010
- **Scope:** `Faculty` entity (code, name uk, name en, archivedAt) extending the shared archivable base; migration; CRUD + archive/restore REST API (`/api/v1/faculties`), mutations need `settings.manage` (Admin has it; grantable via roles), reads for any authenticated user (`?includeArchived` only for `settings.manage`); small generic placeholder list seeded as a REFERENCE seeder through the existing `crm.shared.seed.Seeder` hook (idempotent, real list supplied by humans, no real personal data); public lookup API `FacultyLookup` (exists/active check, resolve by id, list active) for use by INC-015 (`MemberProfile.facultyId`) and INC-021 (directory filter) without exposing entities; audit events and `FacultyCreated/Updated/Archived` domain events; uk/en backend messages for error codes.
- **Out of scope:** admin UI (INC-053), faculty hierarchy, faculty councils `[LATER]`.
- **Acceptance criteria:**
  1. Create/edit/archive/restore require `settings.manage`; others get 403; list and lookup readable by all authenticated users.
  2. Archiving a faculty in use is soft and keeps existing profiles intact; `FacultyLookup` reports archived faculties as not selectable for new or edited profiles.
  3. Names are unique among active faculties; uk and en names both required (validation problem details, uk+en messages).
  4. Seeder is idempotent (second run adds nothing) and registered via the `Seeder` hook as reference data (runs in all profiles, unlike dev fake data).
  5. Audit events emitted with before/after for every mutation; OpenAPI updated and client regenerated when the frontend toolchain is available.
- **Tests required:** unit (uniqueness, archive rules), integration (permission matrix, seeder idempotency, lookup).
- **Notes:** decision Q5. Numbered last but must be scheduled before INC-015. Backend-only so backend work is not blocked by the frontend toolchain.

#### INC-053 — Faculty/institute dictionary admin page (frontend)
- **Status:** DONE
- **Module(s):** frontend `members` (admin settings page)
- **Depends on:** INC-052, INC-008
- **Scope:** settings page listing faculties (active/archived toggle), create/edit form (code, uk and en names), archive/restore with confirmation; page and nav entry visible only with `settings.manage` (backend remains authoritative); faculty select component reusable by INC-016/021.
- **Out of scope:** other settings pages.
- **Acceptance criteria:**
  1. Page and mutating actions are hidden without `settings.manage`; a backend 403 is shown as a localized message.
  2. Server validation errors (duplicate name, missing uk/en name) are displayed per field.
  3. Works at 375 px (cards on mobile), light and dark theme, brand color via tokens; uk+en keys complete.
- **Tests required:** frontend (form validation, permission gating).
- **Notes:** schedule together with INC-016 when the frontend toolchain is available.

#### INC-054 — Flat pagination query parameters in OpenAPI contract and generated client
- **Status:** DONE
- **Module(s):** backend shared `crm.shared.pagination` + controllers using `PageParams` (springdoc configuration only, no behaviour change) / frontend `frontend/src/api` (generated client, shared client)
- **Depends on:** INC-051
- **Scope:** Root cause: controllers bind `@Valid PageParams page`, which springdoc documents as a single object parameter `page`, so openapi-fetch emits deepObject `page[size]=100&page[page]=0` while Spring binds flat `page`/`size`/`sort`. Fix at the contract: make the spec declare flat query params `page`, `size`, `sort` for every operation taking `PageParams` (preferred: springdoc `@ParameterObject` on each `PageParams` argument or one global springdoc customizer/`ParameterObject` registration for `PageParams`, whichever touches least code). Export updated `frontend/openapi/openapi.json`, run `npm run api:generate`. Safety net in `frontend/src/api/client.ts`: a `querySerializer` that serializes any remaining object-valued query parameter flat (no `a[b]` form) — only if still needed after regeneration. Update frontend call sites that pass `{ page: { page, size } }` to the new flat shape. Shared code change is explicitly sanctioned by this increment.
- **Out of scope:** changing backend binding or the pagination response format; new endpoints.
- **Acceptance criteria:**
  1. `openapi.json` contains no parameter named `page` of type object/`PageParams` schema; every paginated operation declares `page`, `size`, `sort` as separate integer/integer/string query parameters (CONTEXT.md §8 API conventions).
  2. Backend test (integration/OpenAPI) asserts the generated `/v3/api-docs` has flat `page`/`size` parameters for at least members list, faculties list and activity levels list, and fails if an object `page` parameter reappears.
  3. Frontend regression test, with a mocked `fetch`, calls the real shared client for members directory, faculties and activity levels and asserts the exact request URL query string is `?page=0&size=100` (no `[`/`%5B` characters).
  4. A paginated list called through the UI against the real backend returns 200 (verified by INC-055 e2e without the workaround).
  5. Client regenerated and committed in the same increment; `npm run build`, `lint`, `test` and `./mvnw verify` pass.
- **Tests required:** backend OpenAPI integration test; frontend unit test on request URL (asserting query string, not mocks of the API layer).
- **Notes:** If the backend and the spec change in different places, controllers must keep behaving identically (binding is already flat). Alternative of only patching the client serializer is rejected because the spec would keep lying to other consumers.

#### INC-055 — Sign-in CSRF token obtained before submit; remove e2e workaround
- **Status:** DONE
- **Module(s):** frontend `auth` (sign-in flow, `frontend/src/api`), `frontend/e2e`
- **Depends on:** INC-054, INC-051
- **Scope:** Sign-in must not race with the first `/auth/me`. Ensure the `XSRF-TOKEN` cookie exists before the login request is sent: the sign-in page/auth provider awaits the initial session/CSRF bootstrap (the form submit button is disabled with a loading state until it completes), and the login mutation, if the cookie is still absent, performs one bootstrap request and then sends the login (no blind retry loop). Remove `flattenPageParams` and `E2E_FLATTEN_PAGE_PARAMS` from `frontend/e2e/support/fixtures.ts` and `deploy/e2e.sh`/docs; re-run the full suite with `bash deploy/e2e.sh`.
- **Out of scope:** changes to backend CSRF configuration.
- **Acceptance criteria:**
  1. Submit is disabled until the CSRF bootstrap completes; a unit test with a delayed `/auth/me` response shows the login request is not sent before the cookie is present and succeeds afterwards.
  2. When the cookie is absent at submit time, the login mutation fetches it first and sends the `X-XSRF-TOKEN` header; test covers this path.
  3. An e2e test submits the sign-in form immediately after navigation (with `/auth/me` artificially delayed) and signs in successfully.
  4. No page-param flattening workaround remains in `frontend/e2e`; grep for `flattenPageParams`/`E2E_FLATTEN_PAGE_PARAMS` is empty.
  5. `bash deploy/e2e.sh` passes fully (desktop and 375 px projects); INC-051 can then be marked DONE.
- **Tests required:** frontend unit (race and missing-cookie path), e2e (full suite without workaround).
- **Notes:** Needs Docker and a running compose stack for e2e.

### Open questions
- **OPEN-Q3** (INC-022): activity level scale (tiers vs numeric) with HR (CONTEXT.md §10 item 3). Plan uses editable reference data with a placeholder scale.
- **OPEN-Q9** (INC-049): who owns servers, domain and TLS at the university (CONTEXT.md §10 item 5), and the backup target. INC-049 is fully parametrized; this is the only item requiring a human before a real deployment.
- **Ukrainian product name** (INC-006): placeholder in the `uk` i18n key; a human provides the final wording.
- **Missing brand variants** (INC-050): no SVG, dark-background logo variant or favicon/PWA icons in `.claude/resources/img/`; to be supplied by a human if wanted.
- **Resolved by the user (2026-10-07):** Q1 logo source in `.claude/resources/img/`; Q2 product name; Q4 individual passwords + brute-force protection with configurable defaults; Q5 faculty is a managed dictionary; Q6 cascade cancel; Q7 author-or-Admin task edit; Q8 20 MB + allow-list; Q10 object history open to all, global log needs `audit.view`.
- **Not planned (requires human decision, outside MVP):** §10 items 1, 2, 4, 6 (events entity, secret ballots, faculty councils, public reporting); `settings.manage` permission has no MVP feature and is only defined in the catalog; §5.11 administrator hard delete is not planned in MVP (soft delete only) `[ASSUMPTION]`.

---

## Plan: Main page (animated login)
Source: .claude/MAIN_PAGE_FEATURE.md (human-approved feature; P0 + P1, P2 optional last) · .claude/CONTEXT.md §6.1 · .claude/CLAUDE.md "Project setup" · Created: 2026-11-12

### Overview
Presentation-only redesign of the sign-in page (`frontend/src/auth/LoginPage.tsx`): rotating KPI campus photos, hero panel, glass login card, intro/exit transitions, ambient effects. Frontend only: no backend, no OpenAPI, no change to `loginRequest`, the CSRF bootstrap (INC-055), session storage or error mapping. Phases: (1) asset pipeline and config, (2) slideshow engine and background, (3) layout, hero and card, (4) motion preference, captions, tagline, success transition, (5) intro and ambient effects, (6) hardening and budgets, (7) optional P2 seasonal accents. Every increment leaves the sign-in page fully working; decorative layers are lazy-loaded so a failure of any of them never blocks the form.

Planner decisions (apply to all increments):
- **Folder:** new code in `frontend/src/auth/login/` (`loginPage.config.ts`, `components/`, `hooks/`, `slideshowImages.ts`). `LoginPage.tsx` and `AuthLayout.tsx` stay where they are (routes and `ChangePasswordPage` import them). `ChangePasswordPage` keeps the existing plain layout and must not regress.
- **Styling:** CSS Modules (`*.module.css`) next to components, using Mantine CSS variables only; no hex literals anywhere (the `noHexColors` test guards TS/TSX; CSS follows the same rule). Animations are CSS keyframes/transitions; **no animation library is added** (none exists; spec §6.2). Text/icons on the dark overlay use white or `studrada` shade 2, never shade 6. Status colours keep their meaning.
- **Images:** originals are copied from `.claude/resources/main_page/images/` to `assets/main_page/images/` (precedent INC-050), content byte-identical but **renamed** to `image_<n>.<original extension>` (`image_1` ... `image_11`, extension kept incl. `.jfif`/`.png`/`.webp`; numbers assigned alphabetically by original name; mapping recorded in `assets/main_page/README.md`; originals under `.claude/` untouched) `[user decision]`. About 8 MB of binaries go into plain git, no Git LFS `[user decision]`. That directory is the single committed source of truth; the spec path `resources/main_page/images/` is replaced by it. Never placed in `src/`; `frontend/dist` is never copied into `src/main/resources/static`.
- **How Vite reads it (spec §6.1 option a):** `server.fs.allow` already includes `..` and `deploy/Dockerfile.frontend` builds with the repository root as context, so `import.meta.glob('<relative>/assets/main_page/images/*.{jpg,jpeg,jfif,png,webp,avif}', ...)` is used exactly like `frontend/src/brand/assets.ts`. Adding/removing a file and rebuilding needs no code change (BG-2). No copy step, no Spring static serving.
- **Build-time optimisation (nothing generated is committed):** one new devDependency, **`vite-imagetools`** (wraps `sharp`). Justification: spec §6.1 requires WebP + AVIF at 2-3 widths through `srcset` with no hand-made copies; this is the smallest Vite-native way, avoids a custom build script, and is build/dev-time only (zero runtime JS, so the 30 KB budget is untouched). Widths 768/1280/1920, formats avif + webp with a webp fallback; the plugin `include` regex is extended so `.jfif` (a JPEG container, `sharp` detects format by content) is processed. Risks verified in INC-056: compatibility with Vite 8, and `sharp` prebuilt binaries on `node:22-alpine`. **Fallback if incompatible:** `frontend/scripts/build-images.mjs` using `sharp` directly from `prebuild`, writing to git-ignored `frontend/.generated/` plus a manifest, same output contract so `slideshowImages.ts` is unchanged. The choice is recorded by the implementator as an ADR draft in `docs/adr/`.
- **Captions:** `assets/main_page/images/captions.json` (`{ "<file name>": { "uk", "en" } }`), read through the same glob (`query: '?raw'`), optional.
- **Budgets that CI cannot measure:** Lighthouse mobile (Performance >= 85, Accessibility >= 95), CLS, 60 fps and "form interactive <= 2.5 s on Fast 3G" are run manually by the validator (procedure in INC-069) and recorded in the report. Automatable ones get scripts/tests: JS <= 30 KB gzip, 768 px image variant <= 250 KB, network allow-list, contrast arithmetic, timer/listener leaks.

### Increments

#### INC-056 — Main page assets, image pipeline, config module, captions lookup
- **Status:** DONE
- **Module(s):** frontend `auth/login` (+ `assets/main_page/`, `frontend/vite.config.ts`, `frontend/package.json`)
- **Depends on:** INC-006, INC-050
- **Scope:** Copy the 11 originals to `assets/main_page/images/` byte-identical in content but renamed `image_<n>.<original extension>` (`image_1` ... `image_11`, alphabetical by original name, including the `.jfif`). Create `captions.json` keyed by the NEW names, with placeholder uk + en entries derived from the ORIGINAL names via the mapping (e.g. original `k-1-evening.jpg` -> `image_N.jpg`: "Головний корпус, вечір" / "Main Building, evening"; originals that give no hint get no entry). Add `assets/main_page/README.md` (purpose, original -> new name mapping table, how to add a photo, caption format, originals under `.claude/` stay untouched). Add `vite-imagetools` (or the documented fallback script) with widths 768/1280/1920 and avif + webp. Create `frontend/src/auth/login/loginPage.config.ts` as the single tunable module: slide 8000 ms, crossfade 1500 ms, reduced-motion interval 15000 ms, Ken Burns scale 1.00-1.08 and direction presets, tagline interval 4000 ms, intro durations, parallax 12/6 px, particle counts 15-30 (halved on phones), indicator cap 10, success transition 900 ms, shake 400 ms, seasonal flag. Create `slideshowImages.ts`: `SlideImage { id, srcSets per format, fallbackSrc }`, `discoverSlides()` and pure `captionFor(id, lang, captions)`. Add `check:images` npm script (node, runs on `frontend/dist`). ADR draft for the pipeline choice.
- **Out of scope:** any UI change, slideshow behaviour.
- **Acceptance criteria:**
  1. `assets/main_page/images/` contains `image_1` ... `image_11` with the original extensions, numbered alphabetically by original name; each is byte-identical to its original in `.claude/resources/main_page/images/` (checksums of original vs renamed copy in the report) and the README mapping table lists all 11 pairs; originals untouched; no file added to `src/`; nothing under `.claude/` staged.
  2. `slideshowImages.ts` globs `jpg, jpeg, jfif, png, webp, avif`; a unit test with an injected glob result proves a new key appears with no code change and an empty result yields `[]` (spec §9.2, BG-2).
  3. `npm run build` emits for each image avif and webp variants at 768, 1280 and 1920 px (never upscaled); `.jfif` is included; no generated image is tracked by git.
  4. `npm run check:images` fails if any 768 px webp variant exceeds 250 KB (§8) and passes for the current set; the 2.5-3.6 MB PNGs are verified to be converted, not served as-is.
  5. `loginPage.config.ts` is the only place for timings/counts; a unit test asserts BG-3 values (8000 / 1500) and slide duration > crossfade.
  6. `captionFor` returns the localized string and returns `undefined` when `captions.json` is missing, malformed, or has no entry for the image; it never returns a file name (CAP-2, spec §9.15).
  7. `npm run build`, `lint`, `test` pass; existing sign-in tests unchanged; `deploy/Dockerfile.frontend` (root context) still builds, or the documented fallback is used.
- **Tests required:** unit (discovery with injected glob, `captionFor`, config sanity); post-build check script.
- **Notes:** about 8 MB of binaries enter plain git without LFS (confirmed by the user). Because the files are renamed, new photos added later may use any name; the `image_<n>` convention is only for the initial set.

#### INC-057 — `useSlideshow` hook and `usePrefersReducedMotion`
- **Status:** TODO
- **Module(s):** frontend `auth/login/hooks`
- **Depends on:** INC-056
- **Scope:** `usePrefersReducedMotion` (matchMedia, reactive, jsdom-safe). `useSlideshow(slides, options)`: shuffle once per mount (Fisher-Yates, injectable RNG); never the same image twice in a row, including at cycle restart (BG-5); timer-driven advance from config; `goTo(index)` for CAP-4; just-in-time preload of the next slide only while the current is shown, a slide is never shown before it loaded and the current one stays longer if the next is not ready (BG-8); failed images are dropped from rotation; pauses on `visibilityState === 'hidden'` and resumes on visible (BG-9); inputs `paused` and `reducedMotion` (15 s interval when reduced, stops when paused, spec §6.3). Returns `{ current, next, progressKey, failedAll }`. All timers/listeners cleaned on unmount.
- **Out of scope:** rendering, Ken Burns, indicator, pause button.
- **Acceptance criteria:**
  1. Unit test (fake timers, injected RNG): over 3 full cycles with 2, 3 and 11 images no two consecutive slides are equal, including across the cycle boundary; with 1 image it stays on it without error.
  2. Unit test: advance occurs after the slide duration and not before; with reduced motion after 15 s; with `paused` never.
  3. Unit test: the next image is requested during the current slide; when its load is pending at the deadline the current slide stays and advance happens only after load; at mount only the first and next images are requested (BG-8).
  4. Unit test: `visibilitychange` to hidden stops advancing, to visible resumes.
  5. Unit test: an image that errors is skipped; when all fail `failedAll` is true.
  6. Unit test: after unmount `vi.getTimerCount()` is 0 and the `visibilitychange`/matchMedia listeners are removed (spec §9.13, §9.15).
  7. `usePrefersReducedMotion` unit test: initial value, reacts to a `change` event, listener removed on unmount.
- **Tests required:** unit (vitest, fake timers).
- **Notes:** No visual change.

#### INC-058 — Background layer: crossfade slideshow, overlay, grain, aurora fallback
- **Status:** TODO
- **Module(s):** frontend `auth/login/components`, `auth/AuthLayout.tsx`
- **Depends on:** INC-057
- **Scope:** `BackgroundSlideshow` (two stacked layers, opacity crossfade of 1.5 s, `<picture>`/`srcset` with `sizes="100vw"` so phones fetch the 768 variant, `aria-hidden`, empty `alt`, not focusable, `pointer-events: none`); dark gradient overlay + vignette (BG-6) and CSS/SVG film grain (BG-7) from Mantine variables; `AuroraFallback` (2-3 blurred drifting blobs in the `studrada` palette, BG-10) shown when there are no slides, all failed, and as the initial state until the first photo has loaded. The layer is a `React.lazy` chunk rendered behind `AuthLayout` on the sign-in route only, so the form renders without waiting for it. Fixed full-viewport, no layout shift.
- **Out of scope:** Ken Burns, parallax, hero, glass card, indicator, captions, pause button.
- **Acceptance criteria:**
  1. Slide `img` elements have `alt=""`, `aria-hidden="true"`, no `tabindex`, `srcset` with 768/1280/1920 widths and `sizes` (spec §6.1).
  2. Crossfade uses `opacity` transitions only, duration read from config (1.5 s).
  3. With an empty slide list, or when every image fires `error`, `AuroraFallback` renders, no broken `<img>` is in the DOM and no `console.error` is emitted (spec §9.3); until the first image loads the aurora is visible (no white screen).
  4. The sign-in form is rendered and usable while the background chunk is still pending (test with a never-resolving lazy import).
  5. New CSS has no hex colours; `noHexColors` and `darkContrast` tests pass; a scan test shows `@keyframes`/`transition` in new `*.module.css` touch only `transform`, `opacity` (and `filter`), never `width|height|top|left|background-position` (spec §6.2).
  6. `auth.test.tsx` and `loginCsrf.test.ts` pass with unchanged assertions; `ChangePasswordPage` renders without the background.
  7. The layer performs no fetch/XHR other than the image URLs.
- **Tests required:** component tests, CSS scan test; manual 375 px light/dark check.
- **Notes:** The overlay is dark in both colour schemes.

#### INC-059 — Two-column layout, hero panel with time-of-day greeting, footer, language switcher
- **Status:** TODO
- **Module(s):** frontend `auth/login/components`, `auth/AuthLayout.tsx`, i18n `uk`/`en`
- **Depends on:** INC-058
- **Scope:** White logo mark (placed here because the hero is its first consumer): copy `.claude/resources/img/Лого знак_біле.png` byte-identical to `assets/brand/logo/logo-mark-white.png`; update `assets/brand/README.md` (new variant for dark backgrounds; a white FULL logo is still missing); expose it in `frontend/src/brand/assets.ts` (new `logo-mark-white` asset with lookup order `logo-mark-white.svg`, `.png`) and a `BrandLogo` variant, with unit tests like the existing ones. Never recolour or generate a logo; if a hook blocks writing to `assets/brand/`, the implementator reports instead of working around it. Responsive layout: >= 1024 px hero left + card right; below it hero collapses to the white mark + title above the card (HERO-1). `HeroPanel` uses the white logo mark directly on the dark overlay (no white chip; the blue full logo is not used on this dark surface; text wordmark if the asset is missing), greeting via `useTimeOfDayGreeting` (05-11 morning, 12-17 day, 18-22 evening, 23-04 night; local time), title and subtitle (HERO-2/3). Footer "© {year} ..." (FT-2). Reuse the existing language switch behaviour (`changeLanguage` / `changeLocale`), moved to the top-right (FT-1). New i18n keys under `auth.login.*` in `uk.json` and `en.json` together.
- **Out of scope:** tagline rotation, staggered reveal, glass styling, motion toggle.
- **Acceptance criteria:**
  1. `useTimeOfDayGreeting` unit tests with injected dates: 04:59 night, 05:00 morning, 11:59 morning, 12:00 day, 17:59 day, 18:00 evening, 22:59 evening, 23:00 night, 00:00 night (spec §9.15).
  2. Title/subtitle/greeting/footer come from i18n keys present in both locale files (key-parity test passes); switching language re-renders them without reload (spec §9.11); no literal user-facing strings in new components.
  3. Title is "Студентська рада КПІ ім. Ігоря Сікорського" / "Student Council of Igor Sikorsky Kyiv Polytechnic Institute"; subtitle "Внутрішня система Студради" / "Student Council internal system" (HERO-2), as keys separate from `app.name`.
  4. At 1024 px hero precedes the card in a two-column grid; at 360/375 px it is one column with no horizontal overflow (spec §9.7).
  5. Text on the overlay uses white or `studrada` shade 2 tokens; the footer year is computed at runtime.
  6. `auth.test.tsx`, e2e `auth.spec.ts` selectors (`getByLabel(/^Login/)`, button "Sign in", `data-testid="login-error"`) pass unchanged; `ChangePasswordPage` unaffected.
- **Tests required:** unit (greeting), component (i18n switching, layout), existing Playwright suite at desktop and 375 px.
- **Notes:** Only the white logo MARK exists; the blue full logo and the chip remain for light surfaces elsewhere (AuthLayout of `ChangePasswordPage`). Extra criteria: (7) `assets/brand/logo/logo-mark-white.png` is byte-identical to the source and `resolveBrandAsset('logo-mark-white')` returns it, and returns `undefined` safely when absent; (8) the hero renders the white mark with an i18n/accessible name and no chip, and `BrandLogo` tests for existing variants still pass.

#### INC-060 — Glass login card wrapping the existing form (floating labels, entrance, reserved slot)
- **Status:** TODO
- **Module(s):** frontend `auth/login/components`, `auth/LoginPage.tsx`
- **Depends on:** INC-059
- **Scope:** Move (not rewrite) the state/mutation/submit logic of `LoginPage` into `useLoginForm` (same `loginRequest`, `trim`, disabled rules, `status === 'loading'` gating from INC-055, same `problemMessage` mapping, same `onSuccess` `setQueryData(SESSION_KEY, ...)`) and render it in `LoginCard`: glassmorphism with an `@supports not (backdrop-filter)` solid fallback (CARD-1); entrance fade + 24 px rise and a short stagger of fields/button (CARD-2); floating labels that work with autofill (CARD-3); animated focus glow/underline (CARD-4) in addition to the existing `focus.css` ring; a `LoginAlternativesSlot` placeholder that renders nothing now and keeps layout unchanged when filled later (CARD-9). No "forgot password"/"remember me" exist today, so none are added (CARD-8).
- **Out of scope:** password toggle labelling, Caps Lock, shake, shine button (INC-061); success transition (INC-065).
- **Acceptance criteria:**
  1. `useLoginForm` preserves behaviour: `auth.test.tsx`, `loginCsrf.test.ts` and the INC-055 race tests pass with unchanged assertions (spec §9.6).
  2. Labels stay real `<label>` elements associated with inputs (`getByLabelText` works) and `autocomplete` stays `username` / `current-password`; floating state is driven by CSS/data attributes so autofilled values never overlap the label.
  3. CSS contains the glass styles and a solid fallback inside `@supports not (backdrop-filter: blur(1px))` (scan test); colours via tokens only.
  4. Entrance uses only `opacity`/`transform` and is disabled under `prefers-reduced-motion`.
  5. `LoginAlternativesSlot` renders `null`; a test shows providing children later does not change card layout classes.
  6. The error `Alert` keeps `role="alert"` and `data-testid="login-error"` and fades in (opacity only).
  7. 375 px: card fully visible without horizontal scroll, light and dark (manual note in report).
- **Tests required:** component tests for unchanged behaviour, CSS scan, e2e `auth.spec.ts` unchanged.
- **Notes:** Any change to `loginRequest` or CSRF handling is forbidden; stop and report if needed.

#### INC-061 — Card interactions: password toggle, Caps Lock hint, shine button, error shake, a11y wiring
- **Status:** TODO
- **Module(s):** frontend `auth/login/components`, i18n
- **Depends on:** INC-060
- **Scope:** Password visibility toggle with i18n accessible label reflecting state (CARD-5); Caps Lock hint via `getModifierState('CapsLock')`, non-blocking, linked by `aria-describedby`; submit button hover lift + light "shine" sweep and loading spinner/disabled (CARD-6); failed login: card shake <= 400 ms (retriggered each failure) plus fade of the existing message (CARD-7); error linked by `aria-describedby` and inside an `aria-live="polite"` container while keeping `role="alert"` and the test id (spec §7).
- **Out of scope:** pause/play preference (INC-062); shake already honours `prefers-reduced-motion` via CSS and is extended there.
- **Acceptance criteria:**
  1. The toggle has an i18n accessible name in uk and en that changes with state; clicking switches the input between `password` and `text`.
  2. With `getModifierState('CapsLock') === true` the hint shows, with false it hides; it uses a warning token (not brand blue) and exists in both languages.
  3. After a failed login (INVALID_CREDENTIALS mocked) the card gets the shake class; the configured shake duration is <= 400 ms (asserted); a second failure re-triggers it; the form stays enabled and re-submittable (spec §9.5).
  4. Under `prefers-reduced-motion: reduce` the shake class is not applied and CSS guards exist for lift/shine.
  5. Enter in either field submits exactly once; the button shows a spinner and is disabled while pending.
  6. The error container has `aria-live="polite"`; inputs reference it with `aria-describedby` only while an error is shown.
  7. Animated properties limited to `transform`/`opacity` (CSS scan).
- **Tests required:** component tests (userEvent), CSS scan.
- **Notes:** None.

#### INC-062 — Motion preference (pause/play), Ken Burns, reduced-motion behaviour
- **Status:** TODO
- **Module(s):** frontend `auth/login` (hooks, `MotionToggle`, `BackgroundSlideshow`)
- **Depends on:** INC-061
- **Scope:** `useMotionPreference` combining `prefers-reduced-motion` and the user's pause stored in `localStorage` (every access in try/catch, key in config); `MotionToggle` (icon, i18n label, `aria-pressed`) last in tab order (FT-3, WCAG 2.2.2); Ken Burns on the visible slide (BG-4): scale 1.00 -> ~1.08, pan direction from presets, never the same direction on consecutive slides; reduced motion/pause per spec §6.3 (no Ken Burns, shake or staggers; plain slow crossfade every ~15 s; pause stops on the current photo). Wire `useSlideshow` `paused` / `reducedMotion`.
- **Out of scope:** particles, parallax, intro (they read this preference in their increments).
- **Acceptance criteria:**
  1. Unit tests: pause is read from `localStorage` on mount, written on toggle, survives remount (spec §9.9); a throwing Storage does not crash and falls back to not paused.
  2. Toggle stops slide advancement and removes the Ken Burns class; pressing again resumes.
  3. With reduced motion emulated: no Ken Burns / stagger / shake classes, interval 15 s, crossfade remains; pause then stops advancing (spec §9.8).
  4. Ken Burns direction differs between consecutive slides (direction picker unit test with injected RNG); animation uses `transform` only.
  5. The toggle is the last focusable element, has a visible focus ring and an i18n name (uk + en).
  6. Existing sign-in tests pass unchanged.
- **Tests required:** unit (preference storage, direction picker), component (toggle effects).
- **Notes:** The shared preference is consumed by INC-064 to INC-068.

#### INC-063 — Photo caption and slide indicator
- **Status:** TODO
- **Module(s):** frontend `auth/login/components`
- **Depends on:** INC-062
- **Scope:** `PhotoCaption` (pin icon, localized caption, fade/slide-in per slide; bottom-left on desktop, top on phone; CAP-1), nothing rendered when `captionFor` is `undefined`. `SlideIndicator`: segmented "stories" bar, active segment fills over the slide lifetime (`scaleX`, duration from config, frozen when paused), up to 10 segments, otherwise one continuous line (CAP-3); segments are buttons with i18n `aria-label` calling `goTo` (CAP-4).
- **Out of scope:** caption editing UI.
- **Acceptance criteria:**
  1. With an entry the pin and localized text appear and update on language switch without reload; with no entry nothing renders and the file name never appears in the DOM (CAP-2).
  2. N <= 10 gives N segments, 11 and 12 slides give the continuous variant; the active segment matches `current`.
  3. Click/tap and Enter/Space on segment k jumps to photo k and restarts progress; segments are keyboard-focusable with visible focus ring.
  4. Fill uses `transform: scaleX` only, stops when paused or tab hidden, static under reduced motion.
  5. No overlap with card/hero at 375 px and 1280 px; fixed-height caption container so there is no layout shift.
  6. Overlay text uses white/shade 2 tokens.
- **Tests required:** component tests; manual 375 px check.
- **Notes:** Captions are placeholders pending the human.

#### INC-064 — Rotating tagline and staggered headline reveal
- **Status:** TODO
- **Module(s):** frontend `auth/login/components`, i18n
- **Depends on:** INC-062, INC-059
- **Scope:** `RotatingTagline` cycling every ~4 s through the i18n phrases (`auth.login.taglines`, exactly 3 by user decision, overriding the spec's 4-6; the component works for any count >= 1, a single phrase is shown static) with the specified word-level transition (HERO-4); polite semantics (no constant announcements). Staggered letter/word reveal of the hero headline on first paint (HERO-5) keeping the full text for assistive tech (`aria-label`, animated spans `aria-hidden`). Reduced motion/pause: plain fade or static, no stagger. Timers cleaned on unmount.
- **Out of scope:** further copy changes.
- **Acceptance criteria:**
  1. `auth.login.taglines` has exactly 3 entries, in this order, in both locale files (parity test): uk "Зі студентом на одній хвилі", "Голос студента КПІ ім. Ігоря Сікорського", "Разом робимо КПІ кращим"; en "On the same wave with a student", "The voice of a student of KPI", "Making KPI better together"; a unit test shows 1 phrase renders static with no timer and 2+ phrases rotate; switching language changes the phrase without reload (spec §9.11).
  2. Fake-timer test: index advances every configured interval, wraps, and no timers remain after unmount (spec §9.13).
  3. Under reduced motion or paused no transform animation class applies and the headline is not split into animated spans.
  4. The `h1` accessible name equals the title key text.
  5. Only `transform`/`opacity` animated; reserved height so no layout shift.
- **Tests required:** unit/component with fake timers.
- **Notes:** None.

#### INC-065 — Success transition into the app
- **Status:** TODO
- **Module(s):** frontend `auth/LoginPage.tsx`, `auth/login`
- **Depends on:** INC-062
- **Scope:** After a successful login play the exit animation: card scales down and fades, background zooms and blurs, full-screen fade, then navigate; total <= 900 ms (config), navigation never later, and immediate under reduced motion/pause. The post-login `Navigate` is deferred through a local `exiting` state (guarded by a ref against double navigation); the redirect for an already authenticated user opening the page stays immediate with no animation (TR-4). Target logic (`from`, `/change-password`) unchanged.
- **Out of scope:** animations on the next page.
- **Acceptance criteria:**
  1. Fake-timer test: after successful login the target route is reached no later than `successTransitionMs` (<= 900); under reduced motion immediately.
  2. An already-authenticated user opening the login route is redirected in the same render with no exiting class (TR-4); `mustChangePassword` and `from` are still honoured.
  3. The existing test "signs in, applies the profile locale and opens the app" keeps its behavioural assertions (timer/reduced-motion mock only to avoid waiting).
  4. A failed login never triggers the exit transition.
  5. Unmount during the transition leaves no timers (spec §9.13); configured value asserted <= 900.
  6. Only `transform`, `opacity`, `filter` animated (CSS scan).
- **Tests required:** unit/component; e2e `auth.spec.ts` green.
- **Notes:** Risk is double navigation or a stuck state; keep a hard timeout fallback.

#### INC-066 — Intro splash
- **Status:** TODO
- **Module(s):** frontend `auth/login/components`
- **Depends on:** INC-062, INC-059
- **Scope:** `IntroSplash`: once per browser session (`sessionStorage` flag in try/catch): the white logo mark (`logo-mark-white` via `BrandLogo`, from INC-059) centred on a dark screen about 0.8 s, then a reveal opens the page (total <= 1.6 s, config) (TR-1); dismissed by click, tap or any key; skipped with reduced motion or paused preference; never delays the form (form rendered and focusable underneath, splash not trapping focus); not shown for authenticated users.
- **Out of scope:** sound, video.
- **Acceptance criteria:**
  1. First mount with empty `sessionStorage` shows the splash and sets the flag; a second mount in the session does not (spec §9.10).
  2. Click, pointer tap and any key dismiss it immediately; listeners are removed after.
  3. Under reduced motion it is never rendered (spec §9.8, §9.10).
  4. Configured total <= 1600 ms (asserted); inputs are in the DOM and focusable while it shows; with fake timers it unmounts after the configured time and leaves no timers.
  5. A throwing `sessionStorage` does not crash (documented choice: splash skipped).
  6. Uses the white logo mark through `BrandLogo` (no chip, no new asset); animates only `transform`/`opacity` (no `clip-path`; the reveal is a scaled circular element).
- **Tests required:** component tests (fake timers); e2e: shown on first navigation, not after reload in the same context.
- **Notes:** None.

#### INC-067 — Mouse parallax and cursor-follow light
- **Status:** TODO
- **Module(s):** frontend `auth/login/hooks`, `components`
- **Depends on:** INC-062
- **Scope:** `useParallax`: rAF-driven, lerp-smoothed; background offset opposite to the pointer up to 12 px, hero with it up to 6 px (AMB-1), transform-only via CSS variables without React re-renders; only for `(pointer: fine)` and hover-capable devices, off for touch, reduced motion and paused. Cursor-follow radial highlight on the card (AMB-3), same eligibility. Listeners and rAF cancelled on unmount and while the tab is hidden.
- **Out of scope:** particles.
- **Acceptance criteria:**
  1. Pure-function tests: targets clamp to 12 / 6 px, background sign opposite to the hero's, lerp converges.
  2. With a coarse pointer, reduced motion or paused, no `pointermove` listener and no rAF are registered (spec §9.7, §9.8).
  3. After unmount `cancelAnimationFrame` and `removeEventListener` were called for everything registered (spy test, spec §9.13).
  4. The loop stops while the tab is hidden.
  5. Pointer movement causes no React re-renders (render counter unchanged).
- **Tests required:** unit (math), hook tests with spies.
- **Notes:** None.

#### INC-068 — Floating particles
- **Status:** TODO
- **Module(s):** frontend `auth/login/components`
- **Depends on:** INC-062
- **Scope:** `Particles`: 15-30 soft dots drifting upward with sway and low opacity, pure CSS (preferred) or one canvas; count halves on phones (viewport < 768 px or coarse pointer); paused while the tab is hidden; not rendered with reduced motion or paused (AMB-2). A `variant` prop with a `default` implementation is the extension point for SEA-1.
- **Out of scope:** seasonal variant.
- **Acceptance criteria:**
  1. Count within 15-30 on desktop and `ceil(count/2)` on a phone viewport (matchMedia mock); values from config.
  2. Zero DOM nodes/canvas under reduced motion or when paused; they return on resume.
  3. Hidden tab pauses the animation; a canvas variant cancels its rAF on unmount (spy).
  4. `aria-hidden`, `pointer-events: none`, below the card in stacking order; only `transform`/`opacity` animated.
  5. Colours via tokens only.
- **Tests required:** component tests.
- **Notes:** None.

#### INC-069 — Hardening: accessibility, contrast, performance budgets, leak and network checks, e2e
- **Status:** TODO
- **Module(s):** frontend `auth/login`, `frontend/e2e`, `frontend/scripts`
- **Depends on:** INC-063, INC-064, INC-065, INC-066, INC-067, INC-068
- **Scope:** (a) Contrast script/test: with `sharp`, compute the brightest-region luminance over all images in `assets/main_page/images/`, apply the configured overlay opacity and assert white text >= 4.5:1 (3:1 large text); tune overlay constants in config if failing. (b) `check:login-budget` after `vite build`: gzip size of the lazy login-decoration chunks plus new CSS <= 30 KB; reuse `check:images`. (c) Playwright specs: 360/375 px with no horizontal scroll and card fully visible; usable at 200 % zoom and phone landscape; request allow-list (static assets, `/api/v1/auth/me`, login only; spec §9.12); reduced-motion emulation (spec §9.8); pause persists across reload (spec §9.9); intro once per session (spec §9.10); empty-image scenario covered at unit level (aurora, no console errors, spec §9.3). (d) Tab-order test: language switcher -> fields -> password toggle -> submit -> motion toggle. (e) Leak test: mount, log in, unmount, assert no timers/rAF/listeners remain (spec §9.13). (f) Written manual procedure for Lighthouse mobile, CLS, Fast-3G form-interactive and 60 fps against `npm run build && npm run preview`. No new test dependency unless justified.
- **Out of scope:** new features, backend.
- **Acceptance criteria:**
  1. The contrast test passes for every image and fails if the overlay opacity drops below the computed threshold (threshold function tested).
  2. `check:login-budget` reports <= 30 KB gzip and fails above; `check:images` passes.
  3. The new Playwright specs pass in desktop and 375 px projects through `bash deploy/e2e.sh`; `auth.spec.ts` and `hr.spec.ts` pass unchanged.
  4. The network allow-list spec fails on any other request.
  5. Leak test and tab-order test pass.
  6. Validator-run Lighthouse mobile (Performance >= 85, Accessibility >= 95), CLS < 0.05 and form-interactive <= 2.5 s on Fast 3G are recorded in the validation report; a miss creates a follow-up increment, it is not waived.
  7. `npm run build`, `lint`, `test` pass; light/dark and 375 px reviewed by hand.
- **Tests required:** e2e (Playwright), unit/script tests, manual Lighthouse.
- **Notes:** Lighthouse, 60 fps and Fast-3G timing cannot run in CI here, by design they are validator-run checks.

#### INC-070 — (Optional, P2) Seasonal snowfall variant of particles
- **Status:** TODO
- **Module(s):** frontend `auth/login/components`, config
- **Depends on:** INC-069
- **Scope:** `Particles` variant `snow` chosen by date (Dec-Feb) as a variant of AMB-2 (SEA-1) and a `seasonalAccents` flag in `loginPage.config.ts` to turn it off (SEA-2). Start only when P0/P1 are validated.
- **Out of scope:** other seasonal content.
- **Acceptance criteria:**
  1. Pure `particleVariantFor(date, flag)` returns `snow` for Dec-Feb and `default` otherwise, always `default` when the flag is off (tests: 30 Nov, 1 Dec, 28 Feb, 29 Feb leap, 1 Mar).
  2. The snow variant reuses the same component, count rules, reduced-motion / pause / hidden-tab behaviour and budgets as INC-068.
  3. No new dependency; budget script still passes.
- **Tests required:** unit.
- **Notes:** Optional; may be dropped by the human.

### Open questions
All non-blocking; placeholders ship so no increment waits on them.
- **Captions (INC-056, INC-063):** final `captions.json` texts for the 11 photos; captions are keyed by the new `image_<n>` names; originals such as `images.jfif` or hash-named PNGs gave no hint of the place, so they get no caption until supplied.
- **Logo (INC-059):** the white logo MARK was supplied (`logo-mark-white.png`) and is used on dark surfaces; a white FULL logo (mark + wordmark) is still missing. No logo is drawn or generated.
- **Product-name wording (INC-059):** HERO-2 title differs from the placeholder `app.name` (open since INC-006); a separate key is used until a human confirms the final wording.

### Conflicts
- No conflict between `MAIN_PAGE_FEATURE.md` and `.claude/CONTEXT.md` found (the spec is presentation-only).
- Applied deviations (not conflicts): spec path `resources/main_page/images/` -> `assets/main_page/images/`; spec folder `src/features/auth/login/` -> `frontend/src/auth/login/`; accent colours via the `studrada` tokens.
- Spec §9.6 "existing login tests pass unchanged": INC-055 changed the sign-in behaviour after the spec was written (submit disabled until `/auth/me`); the plan treats the current tests as the baseline.
- Spec TR-1 suggests a "circular reveal" while §6.2 allows only `transform`/`opacity`/`filter`; the plan follows §6.2 (scaled element, no `clip-path`).
