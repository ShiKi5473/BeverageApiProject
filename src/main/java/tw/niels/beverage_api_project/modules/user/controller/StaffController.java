package tw.niels.beverage_api_project.modules.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.user.dto.StaffDto;
import tw.niels.beverage_api_project.modules.user.dto.UpdateStaffRequestDto;
import tw.niels.beverage_api_project.modules.user.service.UserService;

import java.util.List;

@RestController
@RequestMapping(ApiPaths.API_V1 + "/staff")
@Tag(name = "Staff Management", description = "員工管理 API (查詢、調店、權限設定)")
public class StaffController {

    private final UserService userService;
    private final ControllerHelperService helperService;

    public StaffController(UserService userService, ControllerHelperService helperService) {
        this.userService = userService;
        this.helperService = helperService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER')")
    @Operation(summary = "查詢員工列表", description = "品牌管理員可查所有；店長僅可查本店 (前端可帶 storeId 篩選)")
    public ResponseEntity<List<StaffDto>> getStaffList(@RequestParam(required = false) Long storeId) {
        Long brandId = helperService.getCurrentBrandId();

        // 權限檢查：如果是店長，強制只能查自己的店
        // (這裡依賴 helperService 的身分判斷，若 helperService.getCurrentStoreId() 有值，代表是店長/店員)
        Long currentUserStoreId = helperService.getCurrentStoreId();
        if (currentUserStoreId != null) {
            // 強制覆寫 storeId 為當前使用者的 storeId
            storeId = currentUserStoreId;
        }

        List<StaffDto> staffList = userService.getStaffList(brandId, storeId);
        return ResponseEntity.ok(staffList);
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER')")
    @Operation(summary = "更新員工資料", description = "修改員工職位、所屬分店或停權狀態")
    public ResponseEntity<StaffDto> updateStaff(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateStaffRequestDto requestDto) {

        Long brandId = helperService.getCurrentBrandId();

        // 簡單權限檢查：店長不能隨意調動員工到別店 (UserService 內部會檢查 Store 是否存在於 Brand)
        // 更細緻的權限 (如：店長不能修改 BRAND_ADMIN) 可在 Service 層擴充

        helperService.validateStoreAccess(helperService.getCurrentStoreId()); // 確保發起請求的人有權限 (若是店長)

        StaffDto updatedStaff = userService.updateStaff(brandId, userId, requestDto);
        return ResponseEntity.ok(updatedStaff);
    }
}