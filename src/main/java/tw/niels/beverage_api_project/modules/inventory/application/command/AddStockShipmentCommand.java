package tw.niels.beverage_api_project.modules.inventory.application.command;

import tw.niels.beverage_api_project.modules.inventory.dto.AddShipmentRequestDto;

/**
 * 建立進貨單的命令
 */
public record AddStockShipmentCommand(
    Long brandId,
    Long storeId,
    Long userId,
    AddShipmentRequestDto requestDto
) {}
