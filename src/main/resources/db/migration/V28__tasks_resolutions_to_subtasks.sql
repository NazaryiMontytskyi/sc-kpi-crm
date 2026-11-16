-- Resolutions become subtasks (TASK_TRACKING_FEATURE.md 5.8, ADR-0010, INC-072). Each resolution row becomes one
-- subtask of its task, in this one transaction, deterministically (no now(), no random ids):
--   * subtask id = resolution id; title = first non-blank line of the instruction (<= 200 chars); description = the
--     instruction; deadline = due date (not clamped); creator = author; issued = created; archive state copied;
--   * status = first active status of the mapped category in the parent's Space, with the ADR-0010 fallback;
--   * executor and co-executors -> task_assignee (assigned by the author, at the resolution creation time);
--   * a summary comment (original state, SUBMITTED flagged), one comment per history entry (completion notes and
--     rework comments included), a completion note not carried by any history entry, and "Redirected from X" on a
--     redirected child; original authors and timestamps, marker texts in the default locale uk;
--   * sent resolution_reminder rows -> task_reminder for every assignee (and the author for OVERDUE).
-- The legacy tables are only read (kept read-only, RR-6); no audit rows are written or changed (RR-7).
-- ResolutionMigrationMapping is the executable specification of this script; ResolutionMigrationIntegrationTests
-- proves both agree on fake data. Keep the uk literals equal to tasks.migration.resolution.* in messages_uk.properties.
-- Helper functions live in the session's temporary schema and are dropped at the end.

CREATE FUNCTION pg_temp.v28_id(k TEXT) RETURNS UUID LANGUAGE sql IMMUTABLE AS $$
    SELECT md5('resolution-migration:' || k)::uuid
$$;

-- TipTap document: one paragraph per line, an empty line is an empty paragraph.
CREATE FUNCTION pg_temp.v28_doc(body TEXT) RETURNS JSONB LANGUAGE sql IMMUTABLE AS $$
    SELECT jsonb_build_object('type', 'doc', 'content', jsonb_agg(
               CASE WHEN l.line = '' THEN jsonb_build_object('type', 'paragraph')
                    ELSE jsonb_build_object('type', 'paragraph', 'content',
                             jsonb_build_array(jsonb_build_object('type', 'text', 'text', l.line)))
               END ORDER BY l.n))
      FROM regexp_split_to_table(body, E'\r?\n') WITH ORDINALITY AS l(line, n)
$$;

CREATE FUNCTION pg_temp.v28_title(instruction TEXT) RETURNS TEXT LANGUAGE sql IMMUTABLE AS $$
    SELECT COALESCE(
        (SELECT CASE WHEN length(x.t) <= 200 THEN x.t ELSE left(x.t, 199) || '…' END
           FROM (SELECT btrim(l.line, E' \t\r') AS t, l.n
                   FROM regexp_split_to_table(instruction, E'\n') WITH ORDINALITY AS l(line, n)) x
          WHERE x.t <> ''
          ORDER BY x.n
          LIMIT 1),
        'Резолюція')
$$;

CREATE FUNCTION pg_temp.v28_state(state TEXT) RETURNS TEXT LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE state
               WHEN 'NEW' THEN 'Нова'
               WHEN 'IN_PROGRESS' THEN 'Виконується'
               WHEN 'SUBMITTED' THEN 'На перевірці'
               WHEN 'REWORK' THEN 'На доопрацюванні'
               WHEN 'ACCEPTED' THEN 'Прийнята'
               WHEN 'REDIRECTED' THEN 'Переадресована'
               WHEN 'CANCELED' THEN 'Скасована'
           END
$$;

-- [ASSUMPTION] spec 8 Q7.
CREATE FUNCTION pg_temp.v28_category(state TEXT) RETURNS TEXT LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE state
               WHEN 'NEW' THEN 'TODO'
               WHEN 'IN_PROGRESS' THEN 'IN_PROGRESS'
               WHEN 'REWORK' THEN 'IN_PROGRESS'
               WHEN 'SUBMITTED' THEN 'IN_PROGRESS'
               WHEN 'ACCEPTED' THEN 'DONE'
               WHEN 'CANCELED' THEN 'CANCELED'
               WHEN 'REDIRECTED' THEN 'CANCELED'
           END
$$;

-- First active status (position, then id) of the category; fallback IN_PROGRESS -> TODO, CANCELED -> DONE; then the
-- first active status of the Space; the parent's status if the Space has no active status at all.
CREATE FUNCTION pg_temp.v28_status(p_space UUID, p_category TEXT, p_default UUID) RETURNS UUID
    LANGUAGE sql STABLE AS $$
    SELECT COALESCE(
        (SELECT s.id FROM task_space_status s
          WHERE s.space_id = p_space AND s.archived_at IS NULL AND s.category = p_category
          ORDER BY s.position, s.id LIMIT 1),
        (SELECT s.id FROM task_space_status s
          WHERE s.space_id = p_space AND s.archived_at IS NULL
            AND s.category = CASE p_category WHEN 'IN_PROGRESS' THEN 'TODO' WHEN 'CANCELED' THEN 'DONE' END
          ORDER BY s.position, s.id LIMIT 1),
        (SELECT s.id FROM task_space_status s
          WHERE s.space_id = p_space AND s.archived_at IS NULL
          ORDER BY s.position, s.id LIMIT 1),
        p_default)
$$;

-- Subtasks. A resolution of a task that already is a subtask becomes its sibling (one level only).
INSERT INTO task (id, space_id, title, description, status_id, priority, deadline, custom_field_values,
                  created_at, created_by, updated_at, updated_by, archived_at, archived_by,
                  creator_id, issued_at, parent_task_id, version)
SELECT r.id,
       t.space_id,
       pg_temp.v28_title(r.instruction),
       pg_temp.v28_doc(r.instruction),
       pg_temp.v28_status(t.space_id, pg_temp.v28_category(r.state), t.status_id),
       'NORMAL',
       r.due_date,
       '{}'::jsonb,
       r.created_at, r.created_by, r.updated_at, r.updated_by,
       COALESCE(r.archived_at, t.archived_at),
       CASE WHEN r.archived_at IS NOT NULL THEN r.archived_by ELSE t.archived_by END,
       r.author_id,
       r.created_at,
       COALESCE(t.parent_task_id, t.id),
       0
  FROM resolution r
  JOIN task t ON t.id = r.task_id
 ORDER BY r.created_at, r.id;

-- Assignees: the executor and the co-executors, each user once.
INSERT INTO task_assignee (id, task_id, user_id, assigned_by, assigned_at, created_at, created_by, updated_at,
                           updated_by)
SELECT pg_temp.v28_id('assignee:' || a.resolution_id || ':' || a.user_id),
       a.resolution_id, a.user_id, r.author_id, r.created_at, r.created_at, r.author_id, r.created_at, r.author_id
  FROM (SELECT id AS resolution_id, executor_id AS user_id FROM resolution
        UNION
        SELECT resolution_id, user_id FROM resolution_co_executor) a
  JOIN resolution r ON r.id = a.resolution_id
 ORDER BY a.resolution_id, a.user_id;

-- Summary comment by the system actor: the original state; SUBMITTED awaits acceptance.
INSERT INTO comment (id, target_type, target_id, body, created_at, created_by, updated_at, updated_by)
SELECT pg_temp.v28_id('comment:' || r.id || ':summary'),
       'TASK', r.id,
       pg_temp.v28_doc('[Перенесено з резолюції] Стан резолюції на момент перенесення: «'
                       || pg_temp.v28_state(r.state) || '».'
                       || CASE WHEN r.state = 'SUBMITTED' THEN ' Результат подано, очікує прийняття автором.'
                               ELSE '' END),
       r.updated_at, '00000000-0000-0000-0000-000000000000', r.updated_at, '00000000-0000-0000-0000-000000000000'
  FROM resolution r
 ORDER BY r.id;

-- History entries, by the original actor at the original time; completion notes and rework comments are labelled.
INSERT INTO comment (id, target_type, target_id, body, created_at, created_by, updated_at, updated_by)
SELECT pg_temp.v28_id('comment:' || h.resolution_id || ':history:' || h.position),
       'TASK', h.resolution_id,
       pg_temp.v28_doc('[Перенесено з резолюції] '
                       || CASE WHEN h.from_state IS NULL
                               THEN 'Резолюцію видано, стан «' || pg_temp.v28_state(h.to_state) || '».'
                               ELSE 'Стан змінено: «' || pg_temp.v28_state(h.from_state) || '» → «'
                                    || pg_temp.v28_state(h.to_state) || '».'
                          END
                       || CASE WHEN h.caused_by_resolution_id IS NOT NULL
                               THEN ' (скасовано разом із попередньою резолюцією ланцюжка)' ELSE '' END
                       || CASE WHEN h.comment IS NOT NULL
                               THEN E'\n' || CASE h.to_state
                                                 WHEN 'SUBMITTED' THEN 'Звіт про виконання:'
                                                 WHEN 'REWORK' THEN 'Коментар до доопрацювання:'
                                                 ELSE 'Коментар:'
                                             END || E'\n' || h.comment
                               ELSE '' END),
       h.occurred_at, h.actor_id, h.occurred_at, h.actor_id
  FROM resolution_history h
 ORDER BY h.resolution_id, h.position;

-- A completion note that no SUBMITTED history entry carries (defensive; the application always wrote both).
INSERT INTO comment (id, target_type, target_id, body, created_at, created_by, updated_at, updated_by)
SELECT pg_temp.v28_id('comment:' || r.id || ':note'),
       'TASK', r.id,
       pg_temp.v28_doc('[Перенесено з резолюції] Звіт про виконання:' || E'\n' || r.completion_note),
       r.updated_at, r.executor_id, r.updated_at, r.executor_id
  FROM resolution r
 WHERE r.completion_note IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM resolution_history h
                    WHERE h.resolution_id = r.id AND h.to_state = 'SUBMITTED' AND h.comment = r.completion_note)
 ORDER BY r.id;

-- Redirect chains are flat siblings; the child says where it came from (by the redirecting user, at the redirect).
INSERT INTO comment (id, target_type, target_id, body, created_at, created_by, updated_at, updated_by)
SELECT pg_temp.v28_id('comment:' || r.id || ':redirect'),
       'TASK', r.id,
       pg_temp.v28_doc('[Перенесено з резолюції] Переадресовано з «' || pg_temp.v28_title(p.instruction) || '».'),
       r.created_at, r.author_id, r.created_at, r.author_id
  FROM resolution r
  JOIN resolution p ON p.id = r.parent_resolution_id
 ORDER BY r.id;

-- Reminders already sent: every recipient of the old job (executor, co-executors, the author for OVERDUE), so
-- nothing is sent twice after the cut-over (spec 9.8, NT-2/NT-3).
INSERT INTO task_reminder (task_id, user_id, threshold, deadline, sent_at)
SELECT DISTINCT x.task_id, x.user_id, x.threshold, x.deadline, x.sent_at
  FROM (SELECT rr.resolution_id AS task_id, a.user_id, rr.threshold, rr.due_date AS deadline, rr.sent_at
          FROM resolution_reminder rr
          JOIN (SELECT id AS resolution_id, executor_id AS user_id FROM resolution
                UNION
                SELECT resolution_id, user_id FROM resolution_co_executor) a ON a.resolution_id = rr.resolution_id
        UNION
        SELECT rr.resolution_id, r.author_id, rr.threshold, rr.due_date, rr.sent_at
          FROM resolution_reminder rr
          JOIN resolution r ON r.id = rr.resolution_id
         WHERE rr.threshold = 'OVERDUE') x
 ORDER BY x.task_id, x.user_id, x.threshold, x.deadline;

DROP FUNCTION pg_temp.v28_status(UUID, TEXT, UUID);
DROP FUNCTION pg_temp.v28_category(TEXT);
DROP FUNCTION pg_temp.v28_state(TEXT);
DROP FUNCTION pg_temp.v28_title(TEXT);
DROP FUNCTION pg_temp.v28_doc(TEXT);
DROP FUNCTION pg_temp.v28_id(TEXT);
