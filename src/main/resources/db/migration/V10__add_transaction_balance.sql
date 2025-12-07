/*
  Flyway V10
  Description: 為庫存異動紀錄新增「異動後餘額」欄位，以支援區間耗損計算
*/

ALTER TABLE public.inventory_transactions
    ADD COLUMN balance_after numeric(12, 2);

COMMENT ON COLUMN public.inventory_transactions.balance_after IS '異動後的庫存餘額 (Snapshop Quantity)';