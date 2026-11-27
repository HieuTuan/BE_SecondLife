CREATE TABLE user_bank_accounts (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id UUID NOT NULL REFERENCES users(id),
 bank_code VARCHAR(20) NOT NULL,
 bank_name VARCHAR(120) NOT NULL,
 account_number VARCHAR(30) NOT NULL,
 account_holder_name VARCHAR(150) NOT NULL,
 UNIQUE(user_id, bank_code, bank_name, account_number)
);
