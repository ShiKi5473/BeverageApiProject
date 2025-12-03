-- V7__add_inventory_total_quantity.sql
-- 為原物料表新增「總庫存量」欄位，用於快速檢查與併發控制

-- 1. 新增欄位 (預設為 0)
ALTER TABLE public.inventory_items
    ADD COLUMN total_quantity numeric(12,2) DEFAULT 0.00 NOT NULL;

-- 2. 加上檢查約束，確保庫存不為負數 (資料庫層級的防護)
ALTER TABLE public.inventory_items
    ADD CONSTRAINT chk_inventory_total_quantity CHECK (total_quantity >= 0);

-- 3. 從現有的 inventory_batches 回填數據
--    計算每個 inventory_item_id 的 current_quantity 總和，並寫入 total_quantity
UPDATE public.inventory_items i
SET total_quantity = (
    SELECT COALESCE(SUM(b.current_quantity), 0)
    FROM public.inventory_batches b
    WHERE b.inventory_item_id = i.inventory_item_id
);