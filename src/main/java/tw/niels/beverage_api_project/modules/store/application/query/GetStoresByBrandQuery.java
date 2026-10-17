package tw.niels.beverage_api_project.modules.store.application.query;

/**
 * 查詢品牌下所有分店
 *
 * 由 StoreController 建構，傳遞至 StoreQueryHandler 處理。
 */
public record GetStoresByBrandQuery(
    Long brandId,
    /**
     * 只回傳營業中的分店。
     * 會員／訪客（線上點餐頁）只該看到可下單的分店；員工管理畫面則需要看到全部（含停用）。
     */
    boolean onlyActive
) {
    /** 相容舊呼叫：預設回傳品牌下全部分店（員工用） */
    public GetStoresByBrandQuery(Long brandId) {
        this(brandId, false);
    }
}
