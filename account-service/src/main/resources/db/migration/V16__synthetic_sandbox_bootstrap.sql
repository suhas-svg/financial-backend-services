CREATE TABLE synthetic_sandbox_bootstrap (
    singleton_id SMALLINT PRIMARY KEY CHECK (singleton_id = 1),
    operator_username VARCHAR(255) NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE synthetic_sandbox_seed_accounts (
    seed_key VARCHAR(100) PRIMARY KEY,
    account_id BIGINT NOT NULL UNIQUE REFERENCES accounts(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
