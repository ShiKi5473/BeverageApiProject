/*
  Flyway V9
  Description: 庫存系統重構 - 實作 Product-Variant-Recipe 架構，並將 Batch 轉型為進貨履歷
*/

-- ========================================================
-- 1. 優化原物料表 (Inventory Items) - 增加管理欄位
-- ========================================================
ALTER TABLE public.inventory_items
    ADD COLUMN cost_per_unit numeric(10, 4) DEFAULT 0.0000, -- 單位成本 (用於報表估算)
    ADD COLUMN safety_stock integer DEFAULT 0,              -- 安全水位 (低於此數警示)
    ADD COLUMN is_active boolean DEFAULT true;              -- 軟刪除標記

-- ========================================================
-- 2. 優化批次表 (Inventory Batches) - 下放 Store ID
-- ========================================================
-- 目的：提升查詢效能 (不需 Join 進貨單即可查某店批次)，並支援未來的「店對店調撥」
ALTER TABLE public.inventory_batches
    ADD COLUMN store_id bigint;

-- 資料移轉：從父表 (purchase_shipments) 撈取 store_id 填入子表
UPDATE public.inventory_batches b
SET store_id = s.store_id
FROM public.purchase_shipments s
WHERE b.shipment_id = s.shipment_id;

-- 若有 orphan data (無 shipment 的批次)，暫時設為預設值或處理 (視專案狀況，這裡假設資料完整)
-- 加上 Not Null 與 Foreign Key 約束
ALTER TABLE public.inventory_batches
    ALTER COLUMN store_id SET NOT NULL,
    ADD CONSTRAINT fk_batches_store FOREIGN KEY (store_id) REFERENCES public.stores(store_id) ON DELETE CASCADE;

-- 建立索引加速查詢
CREATE INDEX idx_batches_store_item ON public.inventory_batches (store_id, inventory_item_id);

-- ========================================================
-- 3. 新增銷售規格表 (Product Variants)
-- ========================================================
-- 解決「中杯/大杯」對應不同配方、不同價格的需求
CREATE TABLE public.product_variants (
                                         variant_id bigint NOT NULL, -- TSID
                                         product_id bigint NOT NULL,
                                         name character varying(50) NOT NULL, -- e.g. "中杯", "大杯"
                                         price numeric(10, 2) NOT NULL DEFAULT 0.00,
                                         sku_code character varying(50),      -- e.g. "TEA-BLK-M"

                                         CONSTRAINT pk_product_variants PRIMARY KEY (variant_id),
                                         CONSTRAINT fk_variants_product FOREIGN KEY (product_id) REFERENCES public.products(product_id) ON DELETE CASCADE
);

CREATE INDEX idx_product_variants_product ON public.product_variants (product_id);

-- ========================================================
-- 4. 新增配方表 (Recipes / BOM)
-- ========================================================
-- 核心邏輯：定義銷售項目 (Variant/Option) 扣減多少原料 (Item)
CREATE TABLE public.recipes (
                                recipe_id bigint NOT NULL, -- TSID

    -- 支援兩種來源：飲品規格 OR 加料選項
                                variant_id bigint,
                                option_id bigint,

                                inventory_item_id bigint NOT NULL,
                                quantity numeric(10, 4) NOT NULL, -- 消耗量

                                created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,

                                CONSTRAINT pk_recipes PRIMARY KEY (recipe_id),
                                CONSTRAINT fk_recipes_variant FOREIGN KEY (variant_id) REFERENCES public.product_variants(variant_id) ON DELETE CASCADE,
                                CONSTRAINT fk_recipes_option FOREIGN KEY (option_id) REFERENCES public.product_options(option_id) ON DELETE CASCADE,
                                CONSTRAINT fk_recipes_item FOREIGN KEY (inventory_item_id) REFERENCES public.inventory_items(inventory_item_id) ON DELETE RESTRICT,

    -- 邏輯約束：必須且只能屬於其中一種
                                CONSTRAINT chk_recipe_source CHECK (
                                    (variant_id IS NOT NULL AND option_id IS NULL) OR
                                    (variant_id IS NULL AND option_id IS NOT NULL)
                                    )
);

CREATE INDEX idx_recipes_lookup_variant ON public.recipes (variant_id);
CREATE INDEX idx_recipes_lookup_option ON public.recipes (option_id);

-- ========================================================
-- 5. 新增庫存快照表 (Inventory Snapshots)
-- ========================================================
-- 用於紀錄「最後一次盤點結果」，這是系統顯示庫存的依據
CREATE TABLE public.inventory_snapshots (
                                            snapshot_id bigint NOT NULL, -- TSID
                                            inventory_item_id bigint NOT NULL,
                                            store_id bigint NOT NULL,

                                            quantity numeric(12, 2) NOT NULL DEFAULT 0.00, -- 盤點後的數量
                                            last_checked_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,

                                            CONSTRAINT pk_inventory_snapshots PRIMARY KEY (snapshot_id),
                                            CONSTRAINT fk_snapshots_item FOREIGN KEY (inventory_item_id) REFERENCES public.inventory_items(inventory_item_id) ON DELETE CASCADE,
                                            CONSTRAINT fk_snapshots_store FOREIGN KEY (store_id) REFERENCES public.stores(store_id) ON DELETE CASCADE,

    -- 確保唯一性：一間店的一個物料只有一筆快照
                                            CONSTRAINT uk_snapshots_store_item UNIQUE (store_id, inventory_item_id)
);

-- ========================================================
-- 6. 新增庫存異動紀錄表 (Inventory Transactions)
-- ========================================================
-- 稽核日誌：紀錄所有導致數量變化的原因 (進貨、盤點、報廢)
CREATE TABLE public.inventory_transactions (
                                               transaction_id bigint NOT NULL, -- TSID
                                               inventory_item_id bigint NOT NULL,
                                               store_id bigint NOT NULL,

                                               change_amount numeric(12, 2) NOT NULL, -- 變動量 (+/-)
                                               reason_type character varying(50) NOT NULL, -- e.g. RESTOCK, AUDIT, DAMAGE
                                               operator_id bigint, -- 操作員工
                                               note character varying(255),

                                               created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,

                                               CONSTRAINT pk_inventory_transactions PRIMARY KEY (transaction_id),
                                               CONSTRAINT fk_transactions_item FOREIGN KEY (inventory_item_id) REFERENCES public.inventory_items(inventory_item_id) ON DELETE RESTRICT,
                                               CONSTRAINT fk_transactions_store FOREIGN KEY (store_id) REFERENCES public.stores(store_id) ON DELETE CASCADE
);

CREATE INDEX idx_transactions_history ON public.inventory_transactions (store_id, created_at DESC);