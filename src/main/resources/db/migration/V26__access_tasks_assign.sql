-- Permission tasks.assign (ADR-0010, TASK_TRACKING_FEATURE.md 5.7 RR-3): every role holding resolutions.create,
-- archived roles included, also gets tasks.assign. The resolution keys stay until resolutions are removed.
INSERT INTO role_permission (role_id, permission_key)
SELECT DISTINCT rp.role_id, 'tasks.assign'
  FROM role_permission rp
 WHERE rp.permission_key = 'resolutions.create'
ON CONFLICT (role_id, permission_key) DO NOTHING;
