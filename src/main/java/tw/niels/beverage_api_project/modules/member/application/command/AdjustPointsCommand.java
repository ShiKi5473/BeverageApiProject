package tw.niels.beverage_api_project.modules.member.application.command;

/**
 * 調整會員點數的命令
 */
public record AdjustPointsCommand(
    Long brandId,
    Long memberId,
    Long points,
    String reason,
    String reasonType
) {}
