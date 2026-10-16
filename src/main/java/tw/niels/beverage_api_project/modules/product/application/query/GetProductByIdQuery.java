package tw.niels.beverage_api_project.modules.product.application.query;

/**
 * 查詢單一商品詳細資料的查詢
 * <p>
 * 新增：建立商品後 Controller 需要回傳完整的 ProductResponseDto（原本直接回傳 null body）。
 * </p>
 */
public record GetProductByIdQuery(
    Long brandId,
    Long productId
) {}
