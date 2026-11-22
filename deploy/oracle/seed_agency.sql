-- Run once, after the app has started and Flyway created the tables (connect as ASANZEMAN).
-- The agency the fake Yaghut logs in as (cif 9001).
INSERT INTO azs_business.agency (cif, code, name) VALUES ('9001', '0042', 'Sky Travel');
COMMIT;
