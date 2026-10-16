package tw.niels.beverage_api_project.modules.product.application;

/**
 * 商品相關快取名稱常數。
 * <p>
 * 新增：批次改名時快取名稱被誤改成 "ProductEntity-summary"／"ProductEntity-pos"，
 * 現統一改回 "product-summary"／"product-pos"，並集中成常數，
 * 讓 @Cacheable（ProductQueryHandler）與所有 @CacheEvict（寫入端）引用同一個名稱，避免再次不一致。
 * 兩個快取的 key 皆為 brandId。
 * </p>
 */
public final class ProductCacheNames {

    /** 商品摘要列表快取（value：List&lt;ProductSummaryDto&gt;） */
    public static final String SUMMARY = "product-summary";

    /** POS 完整商品列表快取（value：List&lt;ProductPosDto&gt;） */
    public static final String POS = "product-pos";

    private ProductCacheNames() {
    }
}
