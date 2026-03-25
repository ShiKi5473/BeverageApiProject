package tw.niels.beverage_api_project.modules.inventory.application.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.modules.inventory.application.command.*;
import tw.niels.beverage_api_project.modules.inventory.service.InventoryAuditService;
import tw.niels.beverage_api_project.modules.inventory.service.InventoryDeductionService;
import tw.niels.beverage_api_project.modules.inventory.service.InventoryService;

/**
 * 庫存相關命令處理器
 *
 * 處理庫存進貨、盤點、扣減等寫入操作。
 *
 * 處理流程（以 AddStockShipment 為例）：
 * 1. 接收 AddStockShipmentCommand（含 brandId、storeId、userId、進貨資料）
 * 2. 委派 InventoryService 執行進貨邏輯（過渡期）
 *
 * 備註：過渡期委派既有 Service，待 Phase 3 改為操作 Domain Model。
 */
@Component
public class InventoryCommandHandler {

    private final InventoryService inventoryService;
    private final InventoryDeductionService deductionService;
    private final InventoryAuditService auditService;

    public InventoryCommandHandler(InventoryService inventoryService,
                                   InventoryDeductionService deductionService,
                                   InventoryAuditService auditService) {
        this.inventoryService = inventoryService;
        this.deductionService = deductionService;
        this.auditService = auditService;
    }

    @Transactional
    public void handle(AddStockShipmentCommand command) {
        inventoryService.addShipment(command.brandId(), command.storeId(), command.userId(), command.requestDto());
    }

    @Transactional
    public void handle(PerformInventoryAuditCommand command) {
        auditService.performAudit(command.brandId(), command.storeId(), command.requestDto());
    }

    @Transactional
    public void handle(DeductInventoryCommand command) {
        deductionService.deductInventory(command.brandId(), command.storeId(), command.itemId(), command.quantity());
    }
}
