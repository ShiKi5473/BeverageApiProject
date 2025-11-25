-- V6__insert_promotion_types.sql
-- 寫入促銷活動類型查找表

INSERT INTO public.promotion_types (type_code, description) VALUES
                                                                ('FIXED_AMOUNT', '滿額折抵固定金額 (例: 滿300折30)'),
                                                                ('PERCENTAGE', '百分比折扣 (例: 全館85折)'),
                                                                ('BUY_X_GET_Y', '買X送Y (例: 買一送一、買二送一)'),
                                                                ('BUNDLE_DISCOUNT', '組合優惠 (例: 紅茶+綠茶=50元)'),
                                                                ('GIFT_WITH_PURCHASE', '滿額贈禮 (例: 滿500送環保杯)')
    ON CONFLICT (type_code) DO UPDATE
                                   SET description = EXCLUDED.description;