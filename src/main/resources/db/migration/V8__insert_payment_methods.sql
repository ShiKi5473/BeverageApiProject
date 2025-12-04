-- V8__insert_status_codes.sql
-- 補上缺漏的狀態碼參考資料

-- 1. 插入商品狀態 (Product Status)
INSERT INTO public.product_status_codes (status_code, description) VALUES
                                                                       ('ACTIVE', '上架中'),
                                                                       ('INACTIVE', '已下架'),
                                                                       ('DISCONTINUED', '停售')
ON CONFLICT (status_code) DO NOTHING;

-- 2. 插入訂單狀態 (Order Status)
-- 這些是根據您的 OrderStatus.java Enum 定義的
INSERT INTO public.order_status_codes (status_code, description, is_terminal_state, sort_order) VALUES
                                                                                                    ('PENDING', '待付款/初始狀態', false, 1),
                                                                                                    ('AWAITING_ACCEPTANCE', '等待店家接單', false, 2),
                                                                                                    ('HELD', '暫存訂單', false, 3),
                                                                                                    ('PREPARING', '製作中', false, 4),
                                                                                                    ('READY_FOR_PICKUP', '待取餐', false, 5),
                                                                                                    ('CLOSED', '已結案', true, 6),
                                                                                                    ('CANCELLED', '已取消', true, 7)
ON CONFLICT (status_code) DO NOTHING;

-- 3. 補上付款方式 (Payment Methods) - 這是之前提到的潛在問題，一併補上
INSERT INTO public.payment_methods (code, name) VALUES
                                                    ('CASH', '現金'),
                                                    ('CREDIT_CARD', '信用卡'),
                                                    ('THIRD_PARTY', '第三方支付')
ON CONFLICT (code) DO NOTHING;