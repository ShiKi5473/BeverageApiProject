-- V18：線上訂單欄位與訂單列表查詢索引

-- 1. 線上訪客識別（"GUEST:uuid"），訪客點餐時用於推播訂單狀態通知
ALTER TABLE orders ADD COLUMN guest_ref VARCHAR(64);

-- 2. 線上下單請求 ID（即 ticketId），讓 RabbitMQ 消費者重送訊息時不會重複建立訂單
ALTER TABLE orders ADD COLUMN request_id VARCHAR(32);

-- 3. request_id 部分唯一索引（只約束非 NULL 值，現場訂單不受影響）
CREATE UNIQUE INDEX uk_orders_request_id ON orders (request_id) WHERE request_id IS NOT NULL;

-- 4. 訂單列表查詢（GET /orders?storeId=&status=&from=&to=）使用的複合索引
CREATE INDEX idx_orders_store_status_time ON orders (store_id, status_code, order_time);
