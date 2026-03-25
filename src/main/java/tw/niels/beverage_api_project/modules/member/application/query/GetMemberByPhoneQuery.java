package tw.niels.beverage_api_project.modules.member.application.query;

/**
 * 根據手機號碼查詢會員的查詢
 */
public record GetMemberByPhoneQuery(
    Long brandId,
    String phone
) {}
