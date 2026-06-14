DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'sreadertest') THEN
        CREATE ROLE sreadertest WITH LOGIN PASSWORD 'sreadertest';
    END IF;
END
$$;

SELECT 'CREATE DATABASE sreadertest OWNER sreadertest'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'sreadertest')\gexec

GRANT ALL PRIVILEGES ON DATABASE sreadertest TO sreadertest;
