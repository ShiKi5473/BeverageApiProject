-- 為 order_items 資料表新增 variant_id 欄位
ALTER TABLE order_items
    ADD COLUMN variant_id BIGINT;

-- 建立 Foreign Key 關聯 (確保資料完整性)
ALTER TABLE order_items
    ADD CONSTRAINT fk_order_items_variant
        FOREIGN KEY (variant_id) REFERENCES product_variants(variant_id);

-- 建立索引 (提升查詢與 Join 的效能)
CREATE INDEX idx_order_items_variant ON order_items(variant_id);