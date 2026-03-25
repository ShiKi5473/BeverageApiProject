package tw.niels.beverage_api_project.modules.product.application.command;

import tw.niels.beverage_api_project.modules.product.dto.CreateCategoryRequestDto;

/**
 * 建立分類的命令
 */
public record CreateCategoryCommand(
    Long brandId,
    CreateCategoryRequestDto requestDto
) {}
