package tw.niels.beverage_api_project.modules.member.application.command;

import tw.niels.beverage_api_project.modules.user.dto.CreateUserRequestDto;

/**
 * 更新會員資料的命令
 */
public record UpdateMemberProfileCommand(
    Long brandId,
    Long memberId,
    CreateUserRequestDto.MemberProfileDto profileDto
) {}
