-- Notifications must not hold a schema-level dependency on the users table: modules stay decoupled
-- and users are archived, never deleted. The recipient is validated by the producers.
ALTER TABLE notification DROP CONSTRAINT notification_recipient_id_fkey;
