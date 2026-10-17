package tw.niels.beverage_api_project.modules.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import tw.niels.beverage_api_project.common.annotation.Audit;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.inventory.application.command.*;
import tw.niels.beverage_api_project.modules.inventory.application.handler.InventoryCommandHandler;
import tw.niels.beverage_api_project.modules.inventory.application.handler.InventoryQueryHandler;
import tw.niels.beverage_api_project.modules.inventory.application.query.*;
import tw.niels.beverage_api_project.modules.inventory.dto.AddShipmentRequestDto;
import tw.niels.beverage_api_project.modules.inventory.dto.InventoryAuditItemResponseDto;
import tw.niels.beverage_api_project.modules.inventory.dto.InventoryAuditRequestDto;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(ApiPaths.API_V1 + "/inventory")
@Tag(name = "Inventory Management", description = "庫存管理 (進貨、盤點)")
public class InventoryController {

    private final InventoryCommandHandler commandHandler;
    private final InventoryQueryHandler queryHandler;
    private final ControllerHelperService helperService;

    public InventoryController(InventoryCommandHandler commandHandler,
                               InventoryQueryHandler queryHandler,
                               ControllerHelperService helperService) {
        this.commandHandler = commandHandler;
        this.queryHandler = queryHandler;
        this.helperService = helperService;
    }

    @PostMapping("/shipments")
    // 修改：TESTER（測試人員）比照 STAFF 權限（使用者決策）
    @PreAuthorize("hasAnyRole('MANAGER', 'STAFF', 'TESTER')")
    @Operation(summary = "進貨 (Add Stock)")
    @Audit(action = "INVENTORY_ADD_SHIPMENT")
    public ResponseEntity<?> addShipment(@Valid @RequestBody AddShipmentRequestDto request) {
        StoreEntity store = helperService.getSafeCurrentUserStore();
        Long brandId = helperService.getCurrentBrandId();
        Long userId = helperService.getCurrentUserId();

        commandHandler.handle(new AddStockShipmentCommand(brandId, store.getId(), userId, request));
        return ResponseEntity.ok(Map.of("message", "進貨成功"));
    }

    @GetMapping("/{itemId}")
    // 修改：TESTER（測試人員）比照 STAFF 權限（使用者決策）
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF', 'TESTER')")
    @Operation(summary = "查詢庫存量")
    public ResponseEntity<Map<String, BigDecimal>> checkStock(@PathVariable Long itemId) {
        Long storeId = helperService.getSafeCurrentUserStore().getId();
        helperService.validateStoreAccess(storeId);

        BigDecimal qty = queryHandler.handle(new GetCurrentStockQuery(storeId, itemId));
        return ResponseEntity.ok(Map.of("currentQuantity", qty));
    }

    @PostMapping("/audit")
    // 修改：TESTER（測試人員）比照 STAFF 權限（使用者決策）
    @PreAuthorize("hasAnyRole('MANAGER', 'STAFF', 'TESTER')")
    @Operation(summary = "提交盤點結果")
    @Audit(action = "INVENTORY_AUDIT")
    public ResponseEntity<?> performAudit(@Valid @RequestBody InventoryAuditRequestDto request) {
        StoreEntity store = helperService.getSafeCurrentUserStore();
        Long brandId = helperService.getCurrentBrandId();

        commandHandler.handle(new PerformInventoryAuditCommand(brandId, store.getId(), request));
        return ResponseEntity.ok(Map.of("message", "盤點完成，庫存已更新。"));
    }

    @PostMapping("/{itemId}/deduct")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER')")
    @Operation(summary = "手動扣減庫存 (測試 FIFO)")
    @Audit(action = "INVENTORY_MANUAL_DEDUCT")
    public ResponseEntity<?> manualDeduct(@PathVariable Long itemId, @RequestParam BigDecimal quantity) {
        StoreEntity store = helperService.getSafeCurrentUserStore();
        Long brandId = helperService.getCurrentBrandId();
        helperService.validateStoreAccess(store.getId());

        commandHandler.handle(new DeductInventoryCommand(brandId, store.getId(), itemId, quantity));
        return ResponseEntity.ok(Map.of("message", "扣減成功", "deductedQuantity", quantity));
    }

    @GetMapping("/audit-list")
    // 修改：TESTER（測試人員）比照 STAFF 權限（使用者決策）
    @PreAuthorize("hasAnyRole('MANAGER', 'STAFF', 'TESTER')")
    @Operation(summary = "取得盤點清單")
    public ResponseEntity<List<InventoryAuditItemResponseDto>> getAuditList() {
        StoreEntity store = helperService.getSafeCurrentUserStore();
        Long brandId = helperService.getCurrentBrandId();

        List<InventoryAuditItemResponseDto> list = queryHandler.handle(new GetInventoryAuditListQuery(brandId, store.getId()));
        return ResponseEntity.ok(list);
    }
}
