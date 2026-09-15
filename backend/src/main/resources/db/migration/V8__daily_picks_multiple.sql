-- 대시보드 "오늘의 AI 인사이트"가 계좌당 하루 1종목 -> 최대 3종목(보유 종목 포함)으로 바뀌면서,
-- 계좌+날짜 단위로 유일해야 했던 제약을 계좌+날짜+인사이트 단위로 완화한다 (같은 종목 중복 추천만 방지).
ALTER TABLE daily_picks DROP CONSTRAINT uq_daily_picks_account_date;
ALTER TABLE daily_picks ADD CONSTRAINT uq_daily_picks_account_date_insight UNIQUE (account_id, pick_date, insight_id);
