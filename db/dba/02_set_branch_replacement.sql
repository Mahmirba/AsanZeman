-- D32 / 4-3-5: mark a branch as closed and point it (and everything that pointed at it) to a replacement.
-- Run by the DBA. Usage: replace :closed and :replacement with the full Yaghut branch codes (strings).
-- Never creates chains: A -> B -> C becomes A -> C and B -> C.
DECLARE
    v_closed       azs_business.branch.branch_code%TYPE := '&closed';
    v_replacement  azs_business.branch.branch_code%TYPE := '&replacement';
    v_active       NUMBER;
    v_parent       azs_business.branch.branch_code%TYPE;
BEGIN
    SELECT active, parent_branch INTO v_active, v_parent FROM azs_business.branch WHERE branch_code = v_replacement;
    IF v_active <> 1 OR v_parent <> v_replacement THEN
        RAISE_APPLICATION_ERROR(-20001, 'replacement branch ' || v_replacement || ' is closed or itself replaced');
    END IF;

    UPDATE azs_business.branch
       SET parent_branch = v_replacement, updated_at = SYSTIMESTAMP, updated_by = 'DBA'
     WHERE parent_branch = v_closed;                       -- the closed branch and every branch it replaced

    INSERT INTO azs_audit.audit_log (entity_type, entity_id, action, new_value, user_name, user_role, reason)
    VALUES ('BRANCH', NULL, 'SET_REPLACEMENT',
            '{"closed":"' || v_closed || '","replacement":"' || v_replacement || '"}', 'DBA', 'DBA', 'branch closed');
    COMMIT;
END;
/
