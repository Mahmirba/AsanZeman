-- Run once, after the app has started and Flyway created the tables (connect as ASANZEMAN).
-- The agency the fake Yaghut logs in as (cif 9001).
INSERT INTO azs_business.agency (cif, code, name)
SELECT '9001', '0042', 'Sky Travel' FROM dual WHERE NOT EXISTS (SELECT 1 FROM azs_business.agency WHERE cif = '9001');
COMMIT;

-- Reasons a branch may reject an issuance request with (the admin maintains this list in the real system).
INSERT INTO azs_business.rejection_reason (kind, text, sort_order)
SELECT 'ISSUANCE', 'Documents are incomplete', 1 FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM azs_business.rejection_reason WHERE kind = 'ISSUANCE' AND text = 'Documents are incomplete');
INSERT INTO azs_business.rejection_reason (kind, text, sort_order)
SELECT 'ISSUANCE', 'The customer does not meet the bank policy', 2 FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM azs_business.rejection_reason WHERE kind = 'ISSUANCE' AND text = 'The customer does not meet the bank policy');
COMMIT;

-- Reasons a branch may reject a revocation request with.
INSERT INTO azs_business.rejection_reason (kind, text, sort_order)
SELECT 'REVOCATION', 'The guarantee is still in use', 1 FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM azs_business.rejection_reason WHERE kind = 'REVOCATION' AND text = 'The guarantee is still in use');
COMMIT;
