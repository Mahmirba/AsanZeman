-- Reverses V002. Only for an environment that never held business data.
DROP INDEX azs_audit.ix_audit_user;
DROP INDEX azs_audit.ix_audit_entity;
DROP TABLE azs_audit.audit_log;
DROP TABLE azs_business.tracking_counter;
DROP TABLE azs_business.scheduler_lock;
DROP TABLE azs_business.rejection_reason;
DROP TABLE azs_business.agency;
DROP INDEX azs_business.ix_branch_parent;
DROP TABLE azs_business.branch;
DELETE FROM azs_business."flyway_schema_history" WHERE version = '002';
