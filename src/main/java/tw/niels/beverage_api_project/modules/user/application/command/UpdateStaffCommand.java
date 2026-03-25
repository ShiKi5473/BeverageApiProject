package tw.niels.beverage_api_project.modules.user.application.command;

import tw.niels.beverage_api_project.modules.user.dto.UpdateStaffRequestDto;

/**
 * 更新員工資料的命令
 */
public record UpdateStaffCommand(
    Long brandId,
    Long staffId,
    UpdateStaffRequestDto requestDto
) {}
