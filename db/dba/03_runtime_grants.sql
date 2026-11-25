-- Production: the rights the RUNNING application needs, and nothing more (plan 6-5, D55).
--
-- Run by the DBA as a privileged user after every release that adds tables, once the Flyway migrations have been applied
-- (see docs/runbook.md). 01_create_schemas.sql gives AZS_APP the *ANY TABLE rights so that Flyway can run as AZS_APP
-- in development; in production Flyway runs as a separate migration account and the application is switched to this
-- script's rights, which means:
--   * DML on every business, auth, inquiry and outbox table, and nothing else (no DDL, no *ANY TABLE);
--   * the audit log is INSERT only, so the application can never change or delete what it recorded.
--
-- Usage:  sqlplus / as sysdba @03_runtime_grants.sql AZS_APP
-- The argument is the account the application connects as (spring.datasource.username).

DEFINE runtime_user = &1

-- the broad development rights must be gone, or the grants below limit nothing
BEGIN
  FOR p IN (SELECT privilege FROM dba_sys_privs
             WHERE grantee = UPPER('&runtime_user')
               AND privilege IN ('CREATE ANY TABLE', 'ALTER ANY TABLE', 'DROP ANY TABLE', 'CREATE ANY INDEX',
                                 'DROP ANY INDEX', 'CREATE ANY SEQUENCE', 'DROP ANY SEQUENCE', 'SELECT ANY TABLE',
                                 'INSERT ANY TABLE', 'UPDATE ANY TABLE', 'DELETE ANY TABLE', 'LOCK ANY TABLE',
                                 'COMMENT ANY TABLE')) LOOP
    EXECUTE IMMEDIATE 'REVOKE ' || p.privilege || ' FROM &runtime_user';
  END LOOP;
END;
/

BEGIN
  FOR t IN (SELECT owner, table_name FROM dba_tables
             WHERE owner IN ('AZS_BUSINESS', 'AZS_AUTH', 'AZS_INQUIRY', 'AZS_OUTBOX')
               AND table_name <> 'flyway_schema_history') LOOP
    EXECUTE IMMEDIATE 'GRANT SELECT, INSERT, UPDATE, DELETE ON ' || t.owner || '."' || t.table_name
                      || '" TO &runtime_user';
  END LOOP;
  -- append only: the application writes audit entries and never reads, changes or deletes them
  EXECUTE IMMEDIATE 'GRANT INSERT ON AZS_AUDIT.AUDIT_LOG TO &runtime_user';
END;
/

-- Check: this must list INSERT as the only right on AZS_AUDIT.AUDIT_LOG, and no *ANY TABLE system right.
SELECT table_name, privilege FROM dba_tab_privs
 WHERE grantee = UPPER('&runtime_user') AND owner = 'AZS_AUDIT';
SELECT privilege FROM dba_sys_privs WHERE grantee = UPPER('&runtime_user');
