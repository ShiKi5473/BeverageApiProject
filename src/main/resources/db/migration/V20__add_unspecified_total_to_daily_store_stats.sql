-- V20__add_unspecified_total_to_daily_store_stats.sql
-- [B-6] 日結報表的付款方式細分 (接續 V19)：
--   沒有付款方式 (orders.payment_method_id 為 NULL，例如線上訂單尚未指定付款方式) 的已完成訂單，
--   實收金額原本不屬於 cash / credit_card / third_party 任何一欄，加總會對不上 final_revenue。
--   新增 unspecified_total 記錄「未指定付款方式」的實收金額；原有欄位 (cash_total、line_pay_total 等) 保留不動。
--   對帳公式：cash_total + credit_card_total + third_party_total + unspecified_total (+ line_pay_total，目前恆為 0) = final_revenue

ALTER TABLE public.daily_store_stats
    ADD COLUMN IF NOT EXISTS unspecified_total numeric(12,2) DEFAULT 0.00 NOT NULL;
