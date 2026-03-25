package tw.niels.beverage_api_project.modules.product.application.command;

import tw.niels.beverage_api_project.modules.product.dto.UpdateProductVariantDto;

/**
 * 更新商品規格的命令
 */
public record UpdateProductVariantCommand(
    Long brandId,
    Long variantId,
    UpdateProductVariantDto requestDto
) {}
