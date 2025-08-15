-- Test data initialization for unit tests
INSERT INTO roles (id, name) VALUES (1, 'USER');
INSERT INTO roles (id, name) VALUES (2, 'ADMIN');

-- Test users (password is 'password123' encoded with BCrypt)
INSERT INTO users (id, username, email, password, first_name, last_name, created_at, updated_at) 
VALUES (1, 'testuser', 'test@example.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVMFvO', 'Test', 'User', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (id, username, email, password, first_name, last_name, created_at, updated_at) 
VALUES (2, 'adminuser', 'admin@example.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVMFvO', 'Admin', 'User', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- User role assignments
INSERT INTO user_roles (user_id, role_id) VALUES (1, 1);
INSERT INTO user_roles (user_id, role_id) VALUES (2, 1);
INSERT INTO user_roles (user_id, role_id) VALUES (2, 2);

-- Test accounts
INSERT INTO accounts (id, account_number, balance, account_type, user_id, created_at, updated_at)
VALUES (1, 'ACC123456', 1000.00, 'CHECKING', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO accounts (id, account_number, balance, account_type, user_id, created_at, updated_at)
VALUES (2, 'SAV789012', 5000.00, 'SAVINGS', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO accounts (id, account_number, balance, account_type, user_id, created_at, updated_at)
VALUES (3, 'CC345678', 0.00, 'CREDIT_CARD', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);