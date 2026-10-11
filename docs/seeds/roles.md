# Seed: template roles (`access.template-roles`)

Source: `src/main/java/com/kpi/studentcouncil/crm/access/internal/TemplateRoleSeeder.java`.
Scope `REFERENCE` (all environments), runs once per environment (`seed_history`), idempotent
(a role whose name is already active is skipped). Roles are ordinary, non-system roles: they can be edited,
renamed and archived in the role builder, and a later start never overwrites them.
Each creation emits the usual `ROLE_CHANGE` audit event.

**Status: the mapping below is a proposal and must be reviewed by a human.**

Role names are stored as plain display text (the `Role` model has a single `name` column, unique among active
roles), using the canonical English names from the product specification; the description holds `uk / en` text.

Every role contains the base view permissions: `members.view`, `org.view`, `tasks.view`, `kb.view`,
`dashboard.view`. There is no separate "comment" permission in the catalog; commenting is available to every
authenticated user, so Advisor = view only.

| Permission | Head | Deputy Head | Head of Department | Head of Division | Member | HR | Advisor |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `*.view` (base five) | x | x | x | x | x | x | x |
| `members.create` | | | | | | x | |
| `members.edit` | x | x | | | | x | |
| `members.archive` | | | | | | x | |
| `members.activity.set` | | | | | | x | |
| `org.manage` | x | | | | | | |
| `positions.manage` | x | | | | | | |
| `positions.appoint` | x | x | | | | x | |
| `roles.assign` | x | | | | | x | |
| `tasks.create` | x | x | x | x | x | x | |
| `tasks.edit.any` | x | x | x | x | | | |
| `tasks.archive` | x | x | x | | | | |
| `spaces.manage` | x | x | x | | | | |
| `resolutions.create` | x | x | x | x | x | x | |
| `resolutions.redirect` | x | x | x | x | x | x | |
| `kb.edit` | x | x | x | x | x | x | |
| `kb.archive` | x | x | | | | | |
| `onboarding.manage` | x | x | | | | x | |
| `meetings.manage` | x | x | | | | | |
| `polls.create` | x | x | x | | | | |
| `voting.cast` (defined; behaviour is v2) | x | x | x | | | | |
| `handover.perform` | x | | | | | | |
| `audit.view` | x | | | | | | |

Never granted to a template role (Admin only by default): `roles.manage`, `settings.manage`,
`kb.restricted.view`.
