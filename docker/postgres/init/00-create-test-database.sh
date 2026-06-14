#!/bin/bash
set -e

# Create sreadertest role and database for test.
# POSTGRES_DB/POSTGRES_USER/POSTGRES_PASSWORD are created by the official image entrypoint.
# This script creates only the additional test database and role.

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-'EOSQL'
    DO $$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'sreadertest') THEN
            CREATE ROLE sreadertest WITH LOGIN PASSWORD 'sreadertest';
        END IF;
    END
    $$;
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -tc \
    "SELECT 1 FROM pg_database WHERE datname = 'sreadertest'" \
    | grep -q 1 \
    || psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -c \
       "CREATE DATABASE sreadertest OWNER sreadertest"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -c \
    "GRANT ALL PRIVILEGES ON DATABASE sreadertest TO sreadertest"
