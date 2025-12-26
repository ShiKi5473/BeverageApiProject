-- 為 product_variants 資料表新增 is_deleted 欄位
-- 設定預設值為 FALSE (0)，確保現有資料被視為未刪除
ALTER TABLE product_variants
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;