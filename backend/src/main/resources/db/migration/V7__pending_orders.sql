-- 지정가 주문의 미체결 대기 티켓. ORDERS와 달리 PENDING -> FILLED/CANCELLED 상태 전이를 위해 UPDATE를 허용한다
-- (append-only 규칙은 체결 완료 거래를 기록하는 ORDERS 테이블에만 적용 — CLAUDE.md 절대 규칙).
-- 매수/매도 시 이 행을 SELECT ... FOR UPDATE로 잠근다. 락 획득 순서는 항상 ACCOUNTS -> HOLDINGS -> PENDING_ORDERS.
CREATE TABLE pending_orders (
    id               NUMBER(19)     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id       NUMBER(19)     NOT NULL REFERENCES accounts (id),
    stock_code       VARCHAR2(6)    NOT NULL REFERENCES stocks (code),
    side             VARCHAR2(4)    NOT NULL,
    quantity         NUMBER(19)     NOT NULL CHECK (quantity > 0),
    limit_price      NUMBER(18, 2)  NOT NULL,
    status           VARCHAR2(10)   NOT NULL,
    filled_order_id  NUMBER(19)     REFERENCES orders (id),
    ordered_at       TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    resolved_at      TIMESTAMP
);
CREATE INDEX idx_pending_orders_status_stock ON pending_orders (status, stock_code);
CREATE INDEX idx_pending_orders_account_status ON pending_orders (account_id, status);
