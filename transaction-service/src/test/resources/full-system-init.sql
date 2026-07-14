-- Keep each service's Flyway history and tables isolated while sharing one
-- disposable PostgreSQL container. The services' migrations own all tables.
CREATE SCHEMA IF NOT EXISTS account_service AUTHORIZATION testuser;
CREATE SCHEMA IF NOT EXISTS transaction_service AUTHORIZATION testuser;
