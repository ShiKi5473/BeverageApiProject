-- V19__add_payment_breakdown_to_daily_store_stats.sql
-- [B-6] 日結報表的付款方式細分：
--   原本只有 cash_total / line_pay_total，但系統實際的付款方式代碼是 CASH、CREDIT_CARD、THIRD_PARTY
--   (見 V8__insert_payment_methods.sql)，LINE_PAY 並不存在，信用卡與第三方支付的金額因此沒有被統計。
--   新增 credit_card_total、third_party_total 兩個欄位；原有的 cash_total / line_pay_total 保留不動。

ALTER TABLE public.daily_store_stats
    ADD COLUMN IF NOT EXISTS credit_card_total numeric(12,2) DEFAULT 0.00 NOT NULL,
    ADD COLUMN IF NOT EXISTS third_party_total numeric(12,2) DEFAULT 0.00 NOT NULL;
