package tw.niels.beverage_api_project.modules.product.application.command;

/**
 * 軟刪除商品規格的命令
 */
public record DeleteProductVariantCommand(
    Long brandId,
    Long variantId
) {}
