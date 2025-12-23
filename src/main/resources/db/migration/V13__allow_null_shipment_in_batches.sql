-- 允許 inventory_batches 的 shipment_id 為空 (支援盤盈或非採購來源入庫)
ALTER TABLE public.inventory_batches
    ALTER COLUMN shipment_id DROP NOT NULL;