# Manual undo scripts

Flyway undo is a paid feature, so migrations are forward-fix only (D20). For a risky migration, put a
hand-written rollback here named after the migration it reverses (`U002__reference_tables.sql`), for the
DBA to run by hand. Keep it in step with the migration it undoes.
