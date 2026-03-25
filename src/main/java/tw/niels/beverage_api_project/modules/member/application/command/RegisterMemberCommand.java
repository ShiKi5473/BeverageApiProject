package tw.niels.beverage_api_project.modules.member.application.command;

import tw.niels.beverage_api_project.modules.user.dto.CreateUserRequestDto;

/**
 * 註冊會員的命令
 */
public record RegisterMemberCommand(
    Long brandId,
    CreateUserRequestDto requestDto
) {}
