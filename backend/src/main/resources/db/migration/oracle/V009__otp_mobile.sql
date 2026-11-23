-- V009: the SMS centre's OtpValidate wants the mobile the code was sent to, so the challenge keeps it. Codes sent
-- before this migration have none and are long expired. Forward-fix only (D20).
ALTER TABLE azs_auth.otp_challenge ADD (mobile VARCHAR2(20 CHAR));
