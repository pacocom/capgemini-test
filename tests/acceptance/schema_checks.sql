BEGIN;

DO $$
BEGIN
    ASSERT (SELECT data_type = 'bigint' FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'rooms' AND column_name = 'id');
    ASSERT (SELECT data_type = 'bigint' FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'room_id');
    ASSERT (SELECT data_type = 'bigint'::regtype FROM pg_sequences
            WHERE schemaname = 'public' AND sequencename = 'rooms_id_seq');
    ASSERT (SELECT count(*) = 3 FROM pg_constraint
            WHERE conrelid = 'users'::regclass AND contype = 'u');
    ASSERT NOT EXISTS (SELECT 1 FROM users
            WHERE char_length(name) > 6 OR role NOT IN ('admin', 'superadmin') OR room_id IS NULL);

    BEGIN
        INSERT INTO users(name,email,dni,phone,role,room_id)
        VALUES ('check','schema@verify.test','12345678Z','613000001','admin',1);
        RAISE EXCEPTION 'Duplicate DNI was accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO users(name,email,dni,phone,role,room_id)
        VALUES ('check','schema@verify.test','SCHEMA1','600000001','admin',1);
        RAISE EXCEPTION 'Duplicate phone was accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO users(name,email,dni,phone,role,room_id)
        VALUES ('check','juan@example.com','SCHEMA1','613000001','admin',1);
        RAISE EXCEPTION 'Duplicate email was accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO users(name,email,dni,phone,role,room_id)
        VALUES ('toolong','schema@verify.test','SCHEMA1','613000001','admin',1);
        RAISE EXCEPTION 'Overlong name was accepted';
    EXCEPTION WHEN string_data_right_truncation THEN NULL;
    END;
    BEGIN
        INSERT INTO users(name,email,dni,phone,role,room_id)
        VALUES ('check','schema@verify.test','SCHEMA1','613000001','USER',1);
        RAISE EXCEPTION 'Unsupported role was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO users(name,email,dni,phone,role,room_id)
        VALUES ('check','schema@verify.test','SCHEMA1','613000001','admin',NULL);
        RAISE EXCEPTION 'Missing room was accepted';
    EXCEPTION WHEN not_null_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO users(name,email,dni,phone,role,room_id)
        VALUES ('check','schema@verify.test','SCHEMA1','613000001','admin',999999);
        RAISE EXCEPTION 'Nonexistent room was accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
    BEGIN
        DELETE FROM rooms WHERE id = 1;
        RAISE EXCEPTION 'Room containing users was deleted';
    EXCEPTION WHEN foreign_key_violation OR SQLSTATE '23001' THEN NULL;
    END;
END $$;

ROLLBACK;
