-- V4__migrate_to_tsid.sql
-- 移除核心業務資料表的 IDENTITY (自增) 屬性，改由應用程式端生成 TSID

-- 1. 品牌與分店
ALTER TABLE public.brands ALTER COLUMN brand_id DROP IDENTITY IF EXISTS;
ALTER TABLE public.stores ALTER COLUMN store_id DROP IDENTITY IF EXISTS;

-- 2. 使用者與管理員 (Profile 表的主鍵是 FK，無需修改)
ALTER TABLE public.users ALTER COLUMN user_id DROP IDENTITY IF EXISTS;
ALTER TABLE public.platform_admins ALTER COLUMN admin_id DROP IDENTITY IF EXISTS;

-- 3. 商品相關
ALTER TABLE public.categories ALTER COLUMN category_id DROP IDENTITY IF EXISTS;
ALTER TABLE public.products ALTER COLUMN product_id DROP IDENTITY IF EXISTS;
ALTER TABLE public.option_groups ALTER COLUMN group_id DROP IDENTITY IF EXISTS;
ALTER TABLE public.product_options ALTER COLUMN option_id DROP IDENTITY IF EXISTS;

-- 4. 訂單相關
ALTER TABLE public.orders ALTER COLUMN order_id DROP IDENTITY IF EXISTS;
ALTER TABLE public.order_items ALTER COLUMN order_item_id DROP IDENTITY IF EXISTS;
ALTER TABLE public.invoices ALTER COLUMN invoice_id DROP IDENTITY IF EXISTS;

-- 5. 會員與行銷
ALTER TABLE public.member_points_log ALTER COLUMN log_id DROP IDENTITY IF EXISTS;
ALTER TABLE public.promotions ALTER COLUMN promotion_id DROP IDENTITY IF EXISTS;

-- 6. 庫存與進貨
ALTER TABLE public.inventory_items ALTER COLUMN inventory_item_id DROP IDENTITY IF EXISTS;
ALTER TABLE public.purchase_shipments ALTER COLUMN shipment_id DROP IDENTITY IF EXISTS;
ALTER TABLE public.inventory_batches ALTER COLUMN batch_id DROP IDENTITY IF EXISTS;
ALTER TABLE public.daily_summaries ALTER COLUMN summary_id DROP IDENTITY IF EXISTS;

-- 7. 報表統計
-- 先重命名
ALTER TABLE public.daily_store_stats RENAME COLUMN id TO daily_store_stats_id;
ALTER TABLE public.daily_product_stats RENAME COLUMN id TO daily_product_stats_id;
-- 再移除自增
ALTER TABLE public.daily_store_stats ALTER COLUMN daily_store_stats_id DROP IDENTITY IF EXISTS;
ALTER TABLE public.daily_product_stats ALTER COLUMN daily_product_stats_id DROP IDENTITY IF EXISTS;

-- 注意：
-- 1. 主鍵欄位原本就是 BIGINT，所以不需要 ALTER TYPE。
-- 2. 既有的 Sequence 物件 (如 brands_brand_id_seq) 在 PostgreSQL 中通常會隨著 DROP IDENTITY 自動解除關聯或刪除。
--    為保險起見，您可以選擇手動 DROP SEQUENCE，但在 Flyway 中通常 DROP IDENTITY 就足夠停止自增行為。