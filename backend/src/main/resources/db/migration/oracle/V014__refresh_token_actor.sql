-- The Yaghut user who logged in for an agency, kept with the session so a refresh keeps naming the person (ASVS 7.1).
ALTER TABLE azs_auth.refresh_tokens ADD (actor_name VARCHAR2(100 CHAR));
