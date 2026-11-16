-- Legacy task attachments become FILE artifacts (AR-4, INC-075). task_attachment and task.attachmentIds stay as they
-- are (dropped in a later increment). Idempotent: an artifact gets a deterministic id derived from (task, file), and a
-- (task, file) pair that already has a FILE artifact, active or removed, is skipped, so running this statement again
-- never creates duplicates nor brings back a removed artifact.
-- Who added the file and when is not recorded for attachments: the uploader of the file and the upload time are used.
-- Files that were archived since are migrated too (the reference is data); the API reports them as unavailable.
INSERT INTO task_artifact (id, task_id, artifact_type, file_id, url, title, added_by, added_at, created_at,
                           created_by, updated_at, updated_by)
SELECT md5('task-attachment-artifact:' || a.task_id || ':' || a.file_id)::uuid,
       a.task_id, 'FILE', a.file_id, NULL, NULL, f.uploaded_by, f.created_at, now(), NULL, now(), NULL
  FROM (SELECT DISTINCT ON (task_id, file_id) task_id, file_id, position
          FROM task_attachment
         ORDER BY task_id, file_id, position) a
  JOIN file_object f ON f.id = a.file_id
 WHERE NOT EXISTS (SELECT 1
                     FROM task_artifact x
                    WHERE x.task_id = a.task_id
                      AND x.artifact_type = 'FILE'
                      AND x.file_id = a.file_id)
 ORDER BY a.task_id, a.position
    ON CONFLICT (id) DO NOTHING;
