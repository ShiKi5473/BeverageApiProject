package tw.niels.beverage_api_project.modules.product.application.command;

import java.util.Set;

/**
 * 連結選項群組到商品的命令
 */
public record LinkOptionGroupsCommand(
    Long brandId,
    Long productId,
    Set<Long> groupIds
) {}
