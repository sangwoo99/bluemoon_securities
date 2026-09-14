-- H2(Oracle 근사 모드) 전용 테스트 스키마. 실제 배포용 DDL은 backend/src/main/resources/db/migration/V1__init.sql(Oracle) 참고.
-- Oracle 전용 문법(VARCHAR2/NUMBER/SYSTIMESTAMP/CHECK(... IS JSON))은 H2 표준 타입으로 근사해 미러링한다.

CREATE TABLE users (
    id            BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255)  NOT NULL UNIQUE,
    password_hash VARCHAR(255)  NOT NULL,
    name          VARCHAR(100)  NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE accounts (
    id            BIGINT         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT         NOT NULL UNIQUE REFERENCES users (id),
    cash_balance  DECIMAL(18, 2) NOT NULL,
    created_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE stocks (
    code           VARCHAR(6)     PRIMARY KEY,
    name           VARCHAR(100)   NOT NULL,
    market         VARCHAR(10)    NOT NULL,
    current_price  DECIMAL(18, 2) NOT NULL,
    prev_close     DECIMAL(18, 2) NOT NULL,
    updated_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE holdings (
    id          BIGINT         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id  BIGINT         NOT NULL REFERENCES accounts (id),
    stock_code  VARCHAR(6)     NOT NULL REFERENCES stocks (code),
    quantity    BIGINT         DEFAULT 0 NOT NULL CHECK (quantity >= 0),
    avg_price   DECIMAL(18, 2) DEFAULT 0 NOT NULL,
    updated_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_holdings_account_stock UNIQUE (account_id, stock_code)
);

CREATE TABLE orders (
    id                  BIGINT         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id          BIGINT         NOT NULL REFERENCES accounts (id),
    stock_code          VARCHAR(6)     NOT NULL REFERENCES stocks (code),
    side                VARCHAR(4)     NOT NULL,
    order_type          VARCHAR(6)     NOT NULL,
    quantity            BIGINT         NOT NULL CHECK (quantity > 0),
    limit_price         DECIMAL(18, 2),
    filled_price        DECIMAL(18, 2) NOT NULL,
    filled_quantity     BIGINT         NOT NULL,
    total_amount        DECIMAL(18, 2) NOT NULL,
    status              VARCHAR(10)    NOT NULL,
    cancel_of_order_id  BIGINT         REFERENCES orders (id),
    ordered_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_orders_account_ordered_at ON orders (account_id, ordered_at DESC);
CREATE INDEX idx_orders_account_stock_ordered_at ON orders (account_id, stock_code, ordered_at DESC);

CREATE TABLE pending_orders (
    id               BIGINT         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id       BIGINT         NOT NULL REFERENCES accounts (id),
    stock_code       VARCHAR(6)     NOT NULL REFERENCES stocks (code),
    side             VARCHAR(4)     NOT NULL,
    quantity         BIGINT         NOT NULL CHECK (quantity > 0),
    limit_price      DECIMAL(18, 2) NOT NULL,
    status           VARCHAR(10)    NOT NULL,
    filled_order_id  BIGINT         REFERENCES orders (id),
    ordered_at       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at      TIMESTAMP
);
CREATE INDEX idx_pending_orders_status_stock ON pending_orders (status, stock_code);
CREATE INDEX idx_pending_orders_account_status ON pending_orders (account_id, status);

CREATE TABLE price_snapshots (
    id             BIGINT         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    stock_code     VARCHAR(6)     NOT NULL REFERENCES stocks (code),
    price          DECIMAL(18, 2) NOT NULL,
    snapshot_date  DATE           NOT NULL,
    CONSTRAINT uq_price_snapshots_stock_date UNIQUE (stock_code, snapshot_date)
);

CREATE TABLE account_snapshots (
    id             BIGINT         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id     BIGINT         NOT NULL REFERENCES accounts (id),
    total_value    DECIMAL(18, 2) NOT NULL,
    snapshot_date  DATE           NOT NULL,
    CONSTRAINT uq_account_snapshots_account_date UNIQUE (account_id, snapshot_date)
);

CREATE TABLE ai_insights (
    id             BIGINT     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    stock_code     VARCHAR(6) NOT NULL REFERENCES stocks (code),
    content        CLOB       NOT NULL,
    sources        CLOB       NOT NULL,
    generated_at   TIMESTAMP  NOT NULL
);
CREATE INDEX idx_ai_insights_stock_generated_at ON ai_insights (stock_code, generated_at DESC);

CREATE TABLE daily_picks (
    id          BIGINT  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id  BIGINT  NOT NULL REFERENCES accounts (id),
    insight_id  BIGINT  NOT NULL REFERENCES ai_insights (id),
    pick_date   DATE    NOT NULL,
    CONSTRAINT uq_daily_picks_account_date UNIQUE (account_id, pick_date)
);

CREATE TABLE watchlists (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id  BIGINT      NOT NULL REFERENCES accounts (id),
    stock_code  VARCHAR(6)  NOT NULL REFERENCES stocks (code),
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_watchlists_account_stock UNIQUE (account_id, stock_code)
);

CREATE TABLE top_movers (
    id           BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    stock_code   VARCHAR(6)    NOT NULL REFERENCES stocks (code),
    rank_type    VARCHAR(12)   DEFAULT 'FLUCTUATION' NOT NULL,
    rank_no      INT           NOT NULL,
    change_rate  DECIMAL(6, 2) NOT NULL,
    volume       BIGINT,
    captured_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
