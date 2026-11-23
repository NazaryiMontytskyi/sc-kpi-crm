-- Resolutions are removed (ADR-0010, TASK_TRACKING_FEATURE.md 5.7 RR-3): the keys resolutions.create and
-- resolutions.redirect leave the permission catalog and every role-permission row holding them is deleted.
-- V26 already granted tasks.assign to every role holding resolutions.create; the grant is repeated first so that a
-- role given resolutions.create through the role builder after V26 (archived roles included) keeps the right to
-- assign. Like V26 and V28, this data migration writes no audit rows and changes none (RR-7).
INSERT INTO role_permission (role_id, permission_key)
SELECT DISTINCT rp.role_id, 'tasks.assign'
  FROM role_permission rp
 WHERE rp.permission_key = 'resolutions.create'
ON CONFLICT (role_id, permission_key) DO NOTHING;

DELETE FROM role_permission
 WHERE permission_key IN ('resolutions.create', 'resolutions.redirect');
