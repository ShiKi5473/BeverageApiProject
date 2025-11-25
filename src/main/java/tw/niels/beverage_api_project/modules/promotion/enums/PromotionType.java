package tw.niels.beverage_api_project.modules.promotion.enums;

public enum PromotionType {
    /**
     * 滿額折抵 / 現金折扣
     * 邏輯：滿 minSpend 折 value 元
     * 例：滿 300 折 30
     */
    FIXED_AMOUNT,

    /**
     * 百分比折扣
     * 邏輯：滿 minSpend 打 value 折 (0.85 = 85折)
     * 例：全館 85 折
     */
    PERCENTAGE,

    /**
     * 買 X 送 Y (包含買一送一)
     * 邏輯：購買指定商品 X 件，其中 Y 件免費 (通常是價低者)
     * 需要額外欄位設定 X 和 Y (目前可先寫死為買一送一，或利用 config 欄位)
     */
    BUY_X_GET_Y,

    /**
     * 組合銷售 / 綑綁銷售
     * 邏輯：指定商品組合 (A+B) 享優惠價 value 元
     * 例：紅茶 + 綠茶 = 50 元
     */
    BUNDLE_DISCOUNT,

    /**
     * 滿額贈禮 (贈品)
     * 邏輯：滿 minSpend，訂單自動加入贈品 (0元商品)
     */
    GIFT_WITH_PURCHASE
}