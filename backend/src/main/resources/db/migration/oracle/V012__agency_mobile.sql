-- The mobile number the agency is told about revocations on (optional; an agency without one is not sent SMS).
ALTER TABLE azs_business.agency ADD (mobile VARCHAR2(11 CHAR));
