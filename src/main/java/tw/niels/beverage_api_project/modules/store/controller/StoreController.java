package tw.niels.beverage_api_project.modules.store.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.modules.store.dto.CreateStoreRequestDto;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.store.service.StoreService;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.STORES)
@Tag(name = "Store Management", description = "分店管理 API")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    /**
     * 建立一家新店家
     * 只有品牌管理員 (BRAND_ADMIN) 有權限執行此操作。
     */
    @PostMapping
    @PreAuthorize("hasRole('BRAND_ADMIN')")
    @Operation(summary = "建立新分店", description = "為品牌新增一家分店 (僅品牌管理員)")
    public ResponseEntity<Store> createStore(@Valid @RequestBody CreateStoreRequestDto createStoreRequestDto) {
        Store newStore = storeService.createStore(createStoreRequestDto);
        return new ResponseEntity<>(newStore, HttpStatus.CREATED);
    }
}
