-- V21：補上分類的啟用狀態欄位
-- CategoryEntity / Domain Category 已使用 is_active（啟用／停用分類、findByBrand_IdAndIsActive），
-- 但先前沒有任何遷移腳本建立此欄位，導致查詢分類、POS 商品列表時出現 "column is_active does not exist"。
-- 既有分類一律視為啟用（DEFAULT TRUE），與 Entity 預設值一致。
ALTER TABLE categories ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
