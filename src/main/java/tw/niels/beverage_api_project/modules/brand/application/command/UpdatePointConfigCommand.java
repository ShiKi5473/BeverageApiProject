package tw.niels.beverage_api_project.modules.brand.application.command;

import tw.niels.beverage_api_project.modules.brand.dto.UpdatePointConfigDto;

/**
 * 更新品牌點數配置的命令
 */
public record UpdatePointConfigCommand(
    Long brandId,
    UpdatePointConfigDto requestDto
) {}
