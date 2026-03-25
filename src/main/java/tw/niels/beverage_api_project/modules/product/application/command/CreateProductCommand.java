package tw.niels.beverage_api_project.modules.product.application.command;

import tw.niels.beverage_api_project.modules.product.dto.CreateProductRequestDto;

/**
 * 建立商品的命令
 */
public record CreateProductCommand(
    Long brandId,
    CreateProductRequestDto requestDto
) {}
