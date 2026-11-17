-- Run ONCE by the DBA as a privileged user, before the application starts. Not managed by Flyway.
-- Creates the five schemas (3-4-1), the application user AZS_APP, and the grants.
-- Replace the passwords; Flyway then creates all tables through AZS_APP (migrations use
-- schema-qualified names, so AZS_APP needs CREATE ANY TABLE-style grants limited to these schemas,
-- or the DBA pre-creates the tables - see the comment at the end).

CREATE USER azs_business IDENTIFIED BY "CHANGE_ME" DEFAULT TABLESPACE users QUOTA UNLIMITED ON users;
CREATE USER azs_auth     IDENTIFIED BY "CHANGE_ME" DEFAULT TABLESPACE users QUOTA UNLIMITED ON users;
CREATE USER azs_inquiry  IDENTIFIED BY "CHANGE_ME" DEFAULT TABLESPACE users QUOTA UNLIMITED ON users;
CREATE USER azs_audit    IDENTIFIED BY "CHANGE_ME" DEFAULT TABLESPACE users QUOTA UNLIMITED ON users;
CREATE USER azs_outbox   IDENTIFIED BY "CHANGE_ME" DEFAULT TABLESPACE users QUOTA UNLIMITED ON users;

-- The application connects as AZS_APP only (spring.datasource.username).
CREATE USER azs_app IDENTIFIED BY "CHANGE_ME";
GRANT CREATE SESSION TO azs_app;
ALTER USER azs_app DEFAULT TABLESPACE users QUOTA UNLIMITED ON users;

-- Flyway runs as AZS_APP and must be able to create objects in the five schemas.
GRANT CREATE ANY TABLE, ALTER ANY TABLE, DROP ANY TABLE, CREATE ANY INDEX, DROP ANY INDEX,
      CREATE ANY SEQUENCE, DROP ANY SEQUENCE, SELECT ANY TABLE, INSERT ANY TABLE, UPDATE ANY TABLE,
      DELETE ANY TABLE, LOCK ANY TABLE, COMMENT ANY TABLE TO azs_app;

-- 6-5: the audit log is append-only for the application. Revoke UPDATE/DELETE on it after V002:
--   REVOKE UPDATE ANY TABLE, DELETE ANY TABLE FROM azs_app;  -- then grant per table instead
-- A tighter alternative, preferred in production: have the DBA grant per-table privileges after
-- each release (SELECT/INSERT/UPDATE/DELETE on AZS_BUSINESS.*, INSERT only on AZS_AUDIT.AUDIT_LOG).

-- 3-4-1: transparent data encryption on the tablespace is configured by the DBA (TDE), outside this script.
