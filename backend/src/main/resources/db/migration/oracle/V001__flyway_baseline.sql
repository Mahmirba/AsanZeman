-- V001: Flyway owns the five Asan Zeman schemas (created by db/dba/01_create_schemas.sql).
-- This first migration only records the baseline; tables start in V002.
-- Oracle has no "CREATE TABLE IF NOT EXISTS"; every migration is forward-fix only (D20).
SELECT 1 FROM dual;
