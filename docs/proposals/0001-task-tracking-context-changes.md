# Proposal 0001: CONTEXT.md changes for the task tracking redesign (resolutions retired)

- Status: Proposed (needs a human decision; agents do not edit `CONTEXT.md`)
- Date: 2026-11-23
- Source: `TASK_TRACKING_FEATURE.md` §3 (draft in `.claude/`), implemented on `feature/task-tracking` (INC-071..083)
- Related: ADR-0010 `docs/adr/0010-task-assignment-model-and-resolution-migration.md`

## Why
The feature retires `Resolution` and moves "who does what by when" onto `Task` (assignees, one level of subtasks,
deadline, artifacts, reminders). The code on the branch already follows the feature file; `CONTEXT.md` still
describes resolutions. Until it is updated, the feature file wins only for the items of its §3. This proposal turns
every row of that table into a concrete edit of `CONTEXT.md` (section, current text, proposed text) so a human can
apply it in one pass and then move the feature file out of `.claude/` as its banner asks.

Section numbers below are those of the current `CONTEXT.md`.

## Coverage of feature §3
| # | Feature §3 row | Edits below |
|---|---|---|
| 1 | §3 glossary: `Resolution`, `Redirect`, `CompletionNote`, `Rework` | E1, E2, E3 |
| 2 | §4 domain model: `Task`, `Resolution` | E4 |
| 3 | §5.3 "No task hierarchy" | E5 |
| 4 | §5.3 resolution lifecycle, redirect, completion, rework, suggest DONE | E6, E7 |
| 5 | §5.3 filters "My resolutions", "Issued by me", "resolution author" | E8 |
| 6 | §5.2 permissions `resolutions.create`, `resolutions.redirect` | E9, E10 |
| 7 | §5.9 notification events | E11 |
| 8 | §5.1 profile; §5.7 dashboard; §5.8 search; §5.6 handover lists | E12, E13, E14, E15 |
| 9 | §1.3 "Minimal bureaucracy", "No bottlenecks"; §9 roadmap MVP and "My resolutions" | E16, E17, E18 |
| 10 | §8.2 `tasks` module description | E19 |

E20..E24 are follow-up wording fixes in places the feature table does not name but which would otherwise still
mention resolutions (§5.5, §6, §7, §8.5, §10). They are optional for the conflict, recommended for consistency.

## Edits

### §3 Glossary

**E1 — remove four rows.** Delete the rows `Resolution` (Резолюція), `Redirect` (Переадресація),
`Completion note` (Коментар про виконання) and `Rework` (Повернення на доопрацювання).

**E2 — add four rows** after `Space`:

| Term (EN) | Ukrainian UI label | Code name | Definition |
|---|---|---|---|
| Assignee | Виконавець | `TaskAssignee` | A user who carries out a task. A task may have several assignees; assigning someone else needs `tasks.assign`, assigning oneself only `tasks.create`. Removing an assignee keeps the record (removal time and actor). |
| Subtask | Підзадача | `Task.parentTaskId` | A task inside another task, one level deep (a subtask has no subtasks). It lives in the parent's space; its deadline is not later than the parent's. |
| Artifact | Артефакт | `TaskArtifact` | A result or material attached to a task: an uploaded file or an `http(s)` link with an optional title. Removing it archives it. |
| Deadline | Дедлайн | `Task.deadline` | The date by which a task is due; it is due at the end of that day in `Europe/Kyiv`. |

**E3 — `Handover` row, definition.** Current: "Procedure transferring tasks, resolutions, positions, documents and
history from a predecessor to a successor." Proposed: "Procedure transferring tasks (as assignee and as creator),
positions, documents and history from a predecessor to a successor."

### §4 Domain model

**E4 — `Task` and `Resolution`.**
Current:
> **Task** — `spaceId`, `title`, `description (rich text)`, `statusId`, `priority`, `labels[]`, `dueDate`, `customFieldValues`, `attachments[]`, `relatedTaskIds[]`, `archivedAt`.
> **Resolution** — `taskId`, `authorId`, `executorId`, `coExecutorIds[]`, `instruction`, `dueDate`, `parentResolutionId?`, `state`, `completionNote?`, `history[]`.

Proposed (delete the `Resolution` line):
> **Task** — `spaceId`, `parentTaskId?` (one level of subtasks), `title`, `description (rich text)`, `statusId`, `priority`, `labels[]`, `creatorId`, `issuedAt`, `deadline`, `assignees[]`, `artifacts[]`, `customFieldValues`, `relatedTaskIds[]`, `version` (optimistic locking), `archivedAt`.
> **TaskAssignee** — `taskId`, `userId`, `assignedBy`, `assignedAt`, `removedAt?`, `removedBy?` (a row per assignee, so a per-assignee state can be added later).
> **TaskArtifact** — `taskId`, `type (FILE | LINK)`, `fileId?` or `url?`, `title?`, `addedBy`, `addedAt`, `archivedAt`.

Note under the model: "Legacy `resolution*` tables are kept read-only for one release after the conversion (ADR-0010);
dropping them is a separate, human-approved migration."

### §5.3 Task tracker (rename the heading to "Task tracker `[MVP]`")

**E5 — hierarchy.** Current: "**No task hierarchy** (no project → task → subtask). Tasks can be linked to each other
(`relatedTaskIds`)." Proposed: "**One level of subtasks:** a task may contain subtasks; a subtask cannot contain
subtasks (no project → task → subtask → … chains). A subtask lives in its parent's space and its deadline is not later
than the parent's. Tasks can also be linked to each other (`relatedTaskIds`)."

**E6 — replace the block "Resolutions — the assignment mechanism on top of tasks." and the "Resolution lifecycle"
diagram with all its bullets** (current lines from "**Resolutions — the assignment mechanism…**" to "…suggests moving
the task to a DONE-category status.") by:

> **Assignment — who does what by when, on the task itself.**
> - A task has a **creator**, an **issue date**, a **deadline** and any number of **assignees**.
> - **Who can assign:** anyone with `tasks.assign` — **to anyone**, top-down, **horizontally** and bottom-up, so the Head is not a bottleneck. Without it a user works for themselves: with `tasks.create` they create tasks and subtasks for themselves and may assign or remove themselves.
> - **Subtasks** are how work is split and handed further: anyone who may contribute to a task adds a subtask for themselves, or for another user with `tasks.assign`.
> - **Status:** a task has one status from its space (categories TODO / IN_PROGRESS / DONE / CANCELED), shared by all assignees `[ASSUMPTION: per-assignee status later]`. The creator, any active assignee, a holder of `tasks.edit.any` or an Admin may change it.
> - **Artifacts:** results are attached to the task as files or links (no formal report).
> - **Overdue:** a task whose deadline has passed (end of day, Europe/Kyiv) and whose status is not in a DONE/CANCELED category is highlighted and counted in statistics.
> - **Deadline reminders** go to the assignees before the deadline (default 3 days and 1 day `[ASSUMPTION]`) and on the deadline (overdue, also to the creator).
> - Concurrent edits are detected (optimistic locking); the user is asked to reload.

**E7 — suggest closing.** Covered by E6 plus this bullet in the same block: "When every non-archived, non-canceled
subtask of a task is in a DONE-category status, the system **suggests** closing the task; it never changes the status
by itself `[ASSUMPTION]`."

**E8 — filters and saved views.** Current: "Filters: executor, resolution author, status, due date, labels, space;
saved filters "My resolutions", "Issued by me", "Overdue"." Proposed: "Filters: assignee, creator, status, deadline
range, overdue, labels, space, parent task; built-in views "My tasks" (I am an assignee of an open task, the default
section), "Issued by me" (I created it and assigned it to someone else) and "Overdue"; personal saved filters."

Also: "Views: **list** and **kanban** `[MVP]`; calendar `[v2]`." → append "Selecting a task opens it in a side panel
(desktop) or a full-screen sheet (phone) without leaving the list; `/tasks/:id` is a deep link."

### §5.2 Roles and permissions

**E9 — permission table.** Delete the row `| Resolutions | resolutions.create, resolutions.redirect |`. Change the
Tasks row to: `| Tasks | tasks.view, tasks.create, tasks.assign, tasks.edit.any, tasks.archive, spaces.manage |`.

**E10 — Advisor note and seed.** Current: "**Advisor** (seed): sees everything, can comment; cannot vote or issue
resolutions." Proposed: "**Advisor** (seed): sees everything, can comment; cannot vote or assign tasks to others (no
`tasks.assign`)." Add after the seed bullet: "Seed: `tasks.assign` is granted to Head, Deputy Head, Head of Department,
Head of Division, Member and HR (the roles that had `resolutions.create`)."

### §5.9 Notifications

**E11 — events.** Current: "Events: new resolution to me; redirect; submitted for acceptance; accepted; returned for
rework; due date approaching/passed; @mention; poll opened where I can vote; reminder before closing if I haven't
voted; poll result; handover to me." Proposed: "Events: assigned to a task; removed from a task; deadline changed;
deadline approaching (per configured offset); deadline passed (overdue); subtask added to my task; status changed on a
task I created or am assigned to; @mention; poll opened where I can vote; reminder before closing if I haven't voted;
poll result; handover to me. Notifications already stored for retired resolution events keep rendering."

### Consumers: §5.1, §5.6, §5.7, §5.8

**E12 — §5.1 member profile.** "active tasks/resolutions" → "active and overdue tasks (as assignee)".

**E13 — §5.6 handover lists.** Replace the two bullets "open resolutions where the predecessor is executor or
co-executor;" and "open resolutions where the predecessor is the author (oversight moves to the successor);" and the
bullet "tasks where the predecessor is responsible;" by: "open tasks where the predecessor is an assignee;" and "open
tasks the predecessor created and assigned to others (oversight moves to the successor) `[OPEN: assignee only or
creator too — default both selectable]`;".

**E14 — §5.7 dashboard.** "completed / overdue / active resolutions — per person and per department;" → "completed /
overdue / active tasks (by assignee) — per person and per department;". "A personal "My metrics" widget (my
active/overdue resolutions)" → "A personal "My metrics" widget (my active/overdue tasks as assignee)".

**E15 — §5.8 search.** "across tasks, resolutions, knowledge base articles…" → "across tasks (including assignee names
and subtask titles), knowledge base articles…".

### Principles and roadmap: §1.3, §9

**E16 — §1.3 "Minimal bureaucracy".** Current: "No formal "reports": a resolution is closed with a short "how it was
done" comment." Proposed: "No formal "reports": a task is closed by changing its status, with a comment and its
artifacts (files, links) when useful."

**E17 — §1.3 "No bottlenecks".** Current: "Resolutions can be issued horizontally and bottom-up so the Head does not
become a bottleneck." Proposed: "Tasks and subtasks can be assigned horizontally and bottom-up so the Head does not
become a bottleneck."

**E18 — §9 MVP.** Replace "Resolutions: full lifecycle, redirect, co-executors, rework." by "Assignment on tasks:
several assignees, one level of subtasks, deadlines with reminders, artifacts." Replace "Personal "My resolutions"
mini-dashboard." by "Personal "My tasks" overview (groups Overdue / This week / Later / No deadline, "Issued by me") and
main-page widget."

### Architecture: §8.2

**E19 — module list.** "`tasks` (spaces, tasks, resolutions)" → "`tasks` (spaces, tasks, subtasks, assignees,
artifacts)".

### Follow-up wording (not in feature §3; recommended)

- **E20 — §5.5 Meetings.** "a task/resolution can be created from an adopted decision" → "a task (with assignees) can
  be created from an adopted decision".
- **E21 — §6 Responsiveness.** "(view/issue a resolution, close it with a comment, vote, read an article)" → "(view a
  task, assign it, add a subtask, comment and change its status, vote, read an article)".
- **E22 — §7 Telegram bot.** "possibly actions (vote, close a resolution)" → "possibly actions (vote, change a task
  status)".
- **E23 — §8.5 DoD item 3.** "(especially resolutions, voting, handover)" → "(especially task assignment and subtask
  rules, reminders, voting, handover)".
- **E24 — §10 assumptions.** Delete "After a child resolution is accepted, the parent resolution is submitted for
  acceptance manually." Add: "Task status is shared by all assignees; subtasks are one level deep; a parent task is
  only suggested for closing when its subtasks are done; deadline reminders at 3 days and 1 day; legacy resolution
  tables are kept read-only for one release." Add to `[OPEN]`: "When to drop the legacy resolution tables (human
  decision, ADR-0010)." and "Per-user reminder offsets."

## After applying
1. Move `.claude/TASK_TRACKING_FEATURE.md` to the repository root as its banner says (or archive it), since its §3
   conflicts are then resolved.
2. Remove the "Conflicts" note about this feature from `PLAN.md` (planner/orchestrator).
3. Nothing in the code needs to change: the branch already implements the proposed text.
