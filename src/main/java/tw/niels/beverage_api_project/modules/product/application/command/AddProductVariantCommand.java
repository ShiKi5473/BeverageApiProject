package tw.niels.beverage_api_project.modules.product.application.command;

import tw.niels.beverage_api_project.modules.product.dto.CreateProductVariantDto;

/**
 * 新增商品規格的命令
 */
public record AddProductVariantCommand(
    Long brandId,
    Long productId,
    CreateProductVariantDto requestDto
) {}
