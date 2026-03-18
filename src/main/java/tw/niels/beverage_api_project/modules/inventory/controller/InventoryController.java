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
import tw.niels.beverage_api_project.modules.inventory.dto.AddShipmentRequestDto;
import tw.niels.beverage_api_project.modules.inventory.dto.InventoryAuditItemResponseDto;
import tw.niels.beverage_api_project.modules.inventory.dto.InventoryAuditRequestDto;
import tw.niels.beverage_api_project.modules.inventory.service.InventoryAuditService;
import tw.niels.beverage_api_project.modules.inventory.service.InventoryDeductionService;
import tw.niels.beverage_api_project.modules.inventory.service.InventoryService;
import tw.niels.beverage_api_project.modules.store.entity.Store;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(ApiPaths.API_V1 + "/inventory")
@Tag(name = "Inventory Management", description = "庫存管理 (進貨、盤點)")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryDeductionService deductionService;
    private final InventoryAuditService auditService;
    private final ControllerHelperService helperService;

    // [重構] 拆分後注入三個獨立的 Service，各自負責進貨/扣庫存/盤點
    public InventoryController(InventoryService inventoryService,
                               InventoryDeductionService deductionService,
                               InventoryAuditService auditService,
                               ControllerHelperService helperService) {
        this.inventoryService = inventoryService;
        this.deductionService = deductionService;
        this.auditService = auditService;
        this.helperService = helperService;
    }

    @PostMapping("/shipments")
    @PreAuthorize("hasAnyRole('MANAGER', 'STAFF')") // 通常只有店員需要操作
    @Operation(summary = "進貨 (Add Stock)", description = "員工輸入進貨單，系統自動產生 FIFO 批次")
    @Audit(action = "INVENTORY_ADD_SHIPMENT")
    public ResponseEntity<?> addShipment(@Valid @RequestBody AddShipmentRequestDto request) {
        Store store = helperService.getSafeCurrentUserStore();
        Long brandId = helperService.getCurrentBrandId();

        inventoryService.addShipment(brandId, store.getId(), request);
        return ResponseEntity.ok(Map.of("message", "進貨成功"));
    }

    @GetMapping("/{itemId}")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "查詢庫存量", description = "查詢特定原物料的當前總庫存")
    public ResponseEntity<Map<String, BigDecimal>> checkStock(@PathVariable Long itemId) {
        Long storeId = helperService.getSafeCurrentUserStore().getId();
        helperService.validateStoreAccess(storeId);

        BigDecimal qty = inventoryService.getCurrentStock(storeId, itemId);
        return ResponseEntity.ok(Map.of("currentQuantity", qty));
    }

    /**
     * 執行盤點 (Audit)
     * 權限：店長或店員 (MANAGER, STAFF)
     */
    @PostMapping("/audit")
    @PreAuthorize("hasAnyRole('MANAGER', 'STAFF')")
    @Operation(summary = "提交盤點結果", description = "輸入盤點後的實際數量")
    @Audit(action = "INVENTORY_AUDIT")
    public ResponseEntity<?> performAudit(@Valid @RequestBody InventoryAuditRequestDto request) {

        Store store = helperService.getSafeCurrentUserStore();
        Long brandId = helperService.getCurrentBrandId();

        // [重構] 盤點邏輯委派給 InventoryAuditService
        auditService.performAudit(brandId, store.getId(), request);

        return ResponseEntity.ok(Map.of("message", "盤點完成，庫存已更新。"));
    }


    /**
     * 手動扣減測試 API
     * 目的：讓員工/測試人員可以手動觸發 FIFO 扣減，驗證邏輯是否正確。
     * 未來這段邏輯會被 OrderService 自動呼叫。
     */
    @PostMapping("/{itemId}/deduct")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER')")
    @Operation(summary = "手動扣減庫存 (測試 FIFO)", description = "測試 FIFO 邏輯是否正確扣減過期批次")
    @Audit(action = "INVENTORY_MANUAL_DEDUCT")
    public ResponseEntity<?> manualDeduct(@PathVariable Long itemId,
                                          @RequestParam BigDecimal quantity) {
        Store store = helperService.getSafeCurrentUserStore();

        helperService.validateStoreAccess(store.getId());

        // [重構] FIFO 扣庫存邏輯委派給 InventoryDeductionService
        deductionService.deductInventory(store.getId(), itemId, quantity);

        return ResponseEntity.ok(Map.of("message", "扣減成功", "deductedQuantity", quantity));
    }

    @GetMapping("/audit-list")
    @PreAuthorize("hasAnyRole('MANAGER', 'STAFF')")
    @Operation(summary = "取得盤點清單", description = "取得當前使用者所屬分店的所有原物料庫存")
    public ResponseEntity<List<InventoryAuditItemResponseDto>> getAuditList() {

        Store store = helperService.getSafeCurrentUserStore();
        Long brandId = helperService.getCurrentBrandId();

        // [重構] 盤點清單查詢委派給 InventoryAuditService
        var list = auditService.getAuditList(brandId, store.getId());
        return ResponseEntity.ok(list);
    }
}
