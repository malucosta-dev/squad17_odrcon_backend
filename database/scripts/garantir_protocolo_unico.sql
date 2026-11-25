DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_catalog.pg_index i
        JOIN pg_catalog.pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = i.indkey[0]
        WHERE i.indrelid = 'manifestacao'::regclass
          AND a.attname = 'protocolo_unico'
          AND i.indisunique AND i.indisvalid
          AND i.indnkeyatts = 1 AND i.indpred IS NULL
          AND NOT i.indimmediate
    ) THEN
        RAISE EXCEPTION 'Revise a restrição adiável de protocolo_unico antes de usar ON CONFLICT.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_catalog.pg_index i
        JOIN pg_catalog.pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = i.indkey[0]
        WHERE i.indrelid = 'manifestacao'::regclass
          AND a.attname = 'protocolo_unico'
          AND i.indisunique AND i.indisvalid AND i.indimmediate
          AND i.indnkeyatts = 1 AND i.indpred IS NULL
    ) THEN
        ALTER TABLE manifestacao ADD CONSTRAINT uq_manifestacao_protocolo_unico UNIQUE (protocolo_unico);
    END IF;
END
$$;
