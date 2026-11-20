-- INC-080 (RR-5): resolutions became subtasks (V28) and are no longer indexed by the search module.
-- Remove the resolution documents left in the search module's own derived index table. Index rows are derived data,
-- not business entities, so deleting them is allowed; the resolution tables themselves are untouched (RR-6).
-- The task documents (now with assignee names and subtask titles, including the subtasks created by V28) are rebuilt
-- by the one-off seeder "search.reindex-tasks-as-subtasks" at the next start, or on demand by POST /search/reindex.
DELETE FROM search_document WHERE entity_type = 'RESOLUTION';
