package tw.niels.beverage_api_project.modules.store.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.store.dto.CreateStoreRequestDto;
import tw.niels.beverage_api_project.modules.store.dto.StoreResponseDto;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;
import tw.niels.beverage_api_project.modules.store.repository.StoreJpaRepository;
import tw.niels.beverage_api_project.modules.store.service.StoreService;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.STORES)
@Tag(name = "StoreEntity Management", description = "分店管理 API")
public class StoreController {

    private final StoreService storeService;
    private final StoreJpaRepository storeRepository;
    private final ControllerHelperService helperService;

    public StoreController(StoreService storeService,
                           StoreJpaRepository storeRepository,
                           ControllerHelperService helperService) {
        this.storeService = storeService;
        this.storeRepository = storeRepository;
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
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF', 'MEMBER', 'GUEST')")
    @Operation(summary = "查詢分店列表", description = "取得當前品牌下的所有分店")
    public ResponseEntity<List<StoreResponseDto>> getStoresByBrand() {
        Long brandId = helperService.getCurrentBrandId();

        List<StoreEntity> stores = storeRepository.findByBrand_Id(brandId);

        List<StoreResponseDto> dtos = stores.stream()
                .map(StoreResponseDto::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }
}
