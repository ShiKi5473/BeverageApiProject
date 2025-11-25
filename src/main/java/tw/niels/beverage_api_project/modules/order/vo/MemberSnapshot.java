package tw.niels.beverage_api_project.modules.order.vo;

public record MemberSnapshot(
        Long userId,
        String fullName,
        String phone,
        String email
) {}