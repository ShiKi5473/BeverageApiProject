-- 為 purchase_shipments 資料表新增 invoice_no 欄位
ALTER TABLE purchase_shipments ADD COLUMN invoice_no VARCHAR(50);

-- 建立索引以加速根據廠商單號查詢 (這在對帳時很常用)
CREATE INDEX idx_purchase_shipments_invoice_no ON purchase_shipments(invoice_no);