package tw.niels.beverage_api_project.modules.inventory.application.command;

import tw.niels.beverage_api_project.modules.inventory.dto.InventoryAuditRequestDto;

/**
 * 執行盤點的命令
 */
public record PerformInventoryAuditCommand(
    Long brandId,
    Long storeId,
    InventoryAuditRequestDto requestDto
) {}
