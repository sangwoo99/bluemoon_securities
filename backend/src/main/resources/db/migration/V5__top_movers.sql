CREATE TABLE top_movers (
    id          NUMBER(19)   GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    stock_code  VARCHAR2(6)  NOT NULL REFERENCES stocks (code),
    rank_no     NUMBER(3)    NOT NULL,
    change_rate NUMBER(6, 2) NOT NULL,
    captured_at TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL
);
