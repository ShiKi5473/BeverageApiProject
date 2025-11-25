-- V5__add_snapshot_columns.sql
-- 為訂單與訂單品項新增 JSONB 欄位，用於儲存當下的資料快照

-- 1. 在 orders 表新增 member_snapshot (儲存會員當下資訊)
ALTER TABLE public.orders
    ADD COLUMN member_snapshot jsonb;

-- 2. 在 order_items 表新增 product_snapshot (儲存商品當下資訊)
ALTER TABLE public.order_items
    ADD COLUMN product_snapshot jsonb;