package tw.niels.beverage_api_project.modules.user.application.command;

import tw.niels.beverage_api_project.modules.user.dto.CreateUserRequestDto;

/**
 * 註冊員工的命令
 */
public record RegisterStaffCommand(
    Long brandId,
    CreateUserRequestDto requestDto
) {}
