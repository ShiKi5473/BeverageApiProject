-- 為 orders 資料表新增樂觀鎖版本欄位
-- 用途：配合 JPA @Version 欄位，防止多人同時操作訂單狀態造成 race condition
ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
