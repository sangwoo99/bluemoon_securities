CREATE TABLE users (
    id            NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR2(255) NOT NULL UNIQUE,
    password_hash VARCHAR2(255) NOT NULL,
    name          VARCHAR2(100) NOT NULL,
    created_at    TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE TABLE accounts (
    id            NUMBER(19)     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       NUMBER(19)     NOT NULL UNIQUE REFERENCES users (id),
    cash_balance  NUMBER(18, 2)  NOT NULL,
    created_at    TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at    TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE TABLE stocks (
    code           VARCHAR2(6)    PRIMARY KEY,
    name           VARCHAR2(100)  NOT NULL,
    market         VARCHAR2(10)   NOT NULL,
    current_price  NUMBER(18, 2)  NOT NULL,
    prev_close     NUMBER(18, 2)  NOT NULL,
    updated_at     TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE TABLE holdings (
    id          NUMBER(19)     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id  NUMBER(19)     NOT NULL REFERENCES accounts (id),
    stock_code  VARCHAR2(6)    NOT NULL REFERENCES stocks (code),
    quantity    NUMBER(19)     DEFAULT 0 NOT NULL CHECK (quantity >= 0),
    avg_price   NUMBER(18, 2)  DEFAULT 0 NOT NULL,
    updated_at  TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uq_holdings_account_stock UNIQUE (account_id, stock_code)
);

-- 매수/매도 시 이 행을 SELECT ... FOR UPDATE로 잠근다. 락 획득 순서는 항상 ACCOUNTS -> HOLDINGS (CLAUDE.md 절대 규칙).
CREATE TABLE orders (
    id                  NUMBER(19)     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id          NUMBER(19)     NOT NULL REFERENCES accounts (id),
    stock_code          VARCHAR2(6)    NOT NULL REFERENCES stocks (code),
    side                VARCHAR2(4)    NOT NULL,
    order_type          VARCHAR2(6)    NOT NULL,
    quantity            NUMBER(19)     NOT NULL CHECK (quantity > 0),
    limit_price         NUMBER(18, 2),
    filled_price        NUMBER(18, 2)  NOT NULL,
    filled_quantity     NUMBER(19)     NOT NULL,
    total_amount        NUMBER(18, 2)  NOT NULL,
    status              VARCHAR2(10)   NOT NULL,
    cancel_of_order_id  NUMBER(19)     REFERENCES orders (id),
    ordered_at          TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL
);
CREATE INDEX idx_orders_account_ordered_at ON orders (account_id, ordered_at DESC);
CREATE INDEX idx_orders_account_stock_ordered_at ON orders (account_id, stock_code, ordered_at DESC);

CREATE TABLE price_snapshots (
    id             NUMBER(19)     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    stock_code     VARCHAR2(6)    NOT NULL REFERENCES stocks (code),
    price          NUMBER(18, 2)  NOT NULL,
    snapshot_date  DATE           NOT NULL,
    CONSTRAINT uq_price_snapshots_stock_date UNIQUE (stock_code, snapshot_date)
);

CREATE TABLE account_snapshots (
    id             NUMBER(19)     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id     NUMBER(19)     NOT NULL REFERENCES accounts (id),
    total_value    NUMBER(18, 2)  NOT NULL,
    snapshot_date  DATE           NOT NULL,
    CONSTRAINT uq_account_snapshots_account_date UNIQUE (account_id, snapshot_date)
);

-- sources는 이식성을 위해 네이티브 JSON 타입 대신 CLOB + JSON 제약조건을 사용한다 (docs/db-schema.md 참고).
CREATE TABLE ai_insights (
    id             NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    stock_code     VARCHAR2(6)   NOT NULL REFERENCES stocks (code),
    content        CLOB          NOT NULL,
    sources        CLOB          NOT NULL CHECK (sources IS JSON),
    generated_at   TIMESTAMP     NOT NULL
);
CREATE INDEX idx_ai_insights_stock_generated_at ON ai_insights (stock_code, generated_at DESC);

CREATE TABLE daily_picks (
    id          NUMBER(19)  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id  NUMBER(19)  NOT NULL REFERENCES accounts (id),
    insight_id  NUMBER(19)  NOT NULL REFERENCES ai_insights (id),
    pick_date   DATE        NOT NULL,
    CONSTRAINT uq_daily_picks_account_date UNIQUE (account_id, pick_date)
);
