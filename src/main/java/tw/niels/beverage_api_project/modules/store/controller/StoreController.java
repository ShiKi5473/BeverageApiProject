package tw.niels.beverage_api_project.modules.store.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.common.constants.AuthorityConstants;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.store.application.handler.StoreQueryHandler;
import tw.niels.beverage_api_project.modules.store.application.query.GetStoresByBrandQuery;
import tw.niels.beverage_api_project.modules.store.dto.CreateStoreRequestDto;
import tw.niels.beverage_api_project.modules.store.dto.StoreResponseDto;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;
import tw.niels.beverage_api_project.modules.store.service.StoreService;
import tw.niels.beverage_api_project.security.GuestPrincipal;

import java.util.List;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.STORES)
// 清理(P3)：批次改名殘留 "StoreEntity Management" → "Store Management"
@Tag(name = "Store Management", description = "分店管理 API")
public class StoreController {

    private final StoreService storeService;
    // 清理(P3)：Controller 不再直接依賴 StoreJpaRepository，查詢改走 StoreQueryHandler（CQRS 查端）
    private final StoreQueryHandler storeQueryHandler;
    private final ControllerHelperService helperService;

    public StoreController(StoreService storeService,
                           StoreQueryHandler storeQueryHandler,
                           ControllerHelperService helperService) {
        this.storeService = storeService;
        this.storeQueryHandler = storeQueryHandler;
        this.helperService = helperService;
    }

    /**
     * 建立一家新店家
     * 只有品牌管理員 (BRAND_ADMIN) 有權限執行此操作。
     */
    @PostMapping
    @PreAuthorize("hasRole('BRAND_ADMIN')")
    @Operation(summary = "建立新分店", description = "為品牌新增一家分店 (僅品牌管理員)")
    public ResponseEntity<StoreResponseDto> createStore(@Valid @RequestBody CreateStoreRequestDto createStoreRequestDto) {
        Long currentBrandId = helperService.getCurrentBrandId();
        createStoreRequestDto.setBrandId(currentBrandId);

        StoreEntity newStore = storeService.createStore(createStoreRequestDto);
        // Controller 不再直接回傳 JPA Entity，改回傳 DTO（ID 以字串輸出）
        return new ResponseEntity<>(StoreResponseDto.fromEntity(newStore), HttpStatus.CREATED);
    }

    /**
     * 查詢品牌下所有分店
     * 用途：報表頁面的分店篩選器
     */
    @GetMapping
    // 開放 MEMBER、GUEST：線上點餐頁需讓會員／訪客選擇分店；
    // 仍僅回傳「自己所屬品牌」的分店（getCurrentBrandId 對訪客同樣適用）
    // 修改：TESTER（測試人員）比照 STAFF 權限（使用者決策）
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF', 'TESTER', 'MEMBER', 'GUEST')")
    @Operation(summary = "查詢分店列表", description = "取得當前品牌下的所有分店")
    public ResponseEntity<List<StoreResponseDto>> getStoresByBrand() {
        // 品牌一律取自登入身分（JWT），不接受前端傳入，因此永遠只會看到自己品牌的分店
        Long brandId = helperService.getCurrentBrandId();

        // 會員／訪客只需要「可下單的分店」，只回傳營業中的；員工（報表篩選、分店管理）則看全部
        boolean customerFacing = isMemberOrGuest();

        List<StoreResponseDto> dtos = storeQueryHandler.handle(new GetStoresByBrandQuery(brandId, customerFacing));
        return ResponseEntity.ok(dtos);
    }

    /** 當前呼叫者是否為會員或訪客（非員工） */
    private boolean isMemberOrGuest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> AuthorityConstants.ROLE_MEMBER.equals(a.getAuthority())
                        || GuestPrincipal.ROLE_GUEST.equals(a.getAuthority()));
    }
}
