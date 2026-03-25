package tw.niels.beverage_api_project.modules.inventory.application.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.modules.inventory.application.query.*;
import tw.niels.beverage_api_project.modules.inventory.dto.InventoryAuditItemResponseDto;
import tw.niels.beverage_api_project.modules.inventory.service.InventoryAuditService;
import tw.niels.beverage_api_project.modules.inventory.service.InventoryService;

import java.math.BigDecimal;
import java.util.List;

/**
 * 庫存相關查詢處理器（唯讀）
 *
 * 處理流程：
 * 1. 接收查詢條件（盤點清單 或 當前庫存量）
 * 2. 委派 InventoryService / InventoryAuditService 執行查詢
 * 3. 回傳查詢結果
 */
@Component
public class InventoryQueryHandler {

    private final InventoryService inventoryService;
    private final InventoryAuditService auditService;

    public InventoryQueryHandler(InventoryService inventoryService,
                                 InventoryAuditService auditService) {
        this.inventoryService = inventoryService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<InventoryAuditItemResponseDto> handle(GetInventoryAuditListQuery query) {
        return auditService.getAuditList(query.brandId(), query.storeId());
    }

    @Transactional(readOnly = true)
    public BigDecimal handle(GetCurrentStockQuery query) {
        return inventoryService.getCurrentStock(query.storeId(), query.itemId());
    }
}
