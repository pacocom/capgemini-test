-- Align the schema and sample data with the README requirements.
UPDATE users SET name = 'Juan', role = 'admin'
WHERE email = 'juan@example.com';
UPDATE users SET name = 'María', role = 'superadmin'
WHERE email = 'maria@example.com';

ALTER TABLE users DROP CONSTRAINT fk_room;
ALTER TABLE rooms ALTER COLUMN id TYPE BIGINT;
ALTER SEQUENCE rooms_id_seq AS BIGINT;
ALTER TABLE users ALTER COLUMN room_id TYPE BIGINT;
ALTER TABLE users ALTER COLUMN room_id SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT fk_room
    FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE RESTRICT;

ALTER TABLE users ALTER COLUMN name TYPE VARCHAR(6);
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('admin', 'superadmin'));
