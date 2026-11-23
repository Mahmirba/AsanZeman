-- The hourly 7-day rule looks for requests by status and registration time.
CREATE INDEX azs_business.ix_request_status_registered ON azs_business.request (status, registered_at);
