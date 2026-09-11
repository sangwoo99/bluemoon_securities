CREATE TABLE watchlists (
    id          NUMBER(19)  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id  NUMBER(19)  NOT NULL REFERENCES accounts (id),
    stock_code  VARCHAR2(6) NOT NULL REFERENCES stocks (code),
    created_at  TIMESTAMP   DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uq_watchlists_account_stock UNIQUE (account_id, stock_code)
);
