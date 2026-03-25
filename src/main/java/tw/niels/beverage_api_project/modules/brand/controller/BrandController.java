package tw.niels.beverage_api_project.modules.brand.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import tw.niels.beverage_api_project.common.annotation.Audit;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.brand.application.command.UpdatePointConfigCommand;
import tw.niels.beverage_api_project.modules.brand.application.handler.BrandCommandHandler;
import tw.niels.beverage_api_project.modules.brand.application.handler.BrandQueryHandler;
import tw.niels.beverage_api_project.modules.brand.application.query.GetAllBrandsQuery;
import tw.niels.beverage_api_project.modules.brand.application.query.GetBrandByIdQuery;
import tw.niels.beverage_api_project.modules.brand.dto.UpdatePointConfigDto;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.entity.BrandPointConfig;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.BRANDS)
@Tag(name = "Brand Management", description = "品牌資訊與設定 API")
public class BrandController {

    private final BrandCommandHandler commandHandler;
    private final BrandQueryHandler queryHandler;
    private final ControllerHelperService helperService;

    public BrandController(BrandCommandHandler commandHandler,
                           BrandQueryHandler queryHandler,
                           ControllerHelperService helperService) {
        this.commandHandler = commandHandler;
        this.queryHandler = queryHandler;
        this.helperService = helperService;
    }

    @GetMapping
    @Operation(summary = "取得所有品牌列表 (僅限平台管理員)")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<Brand>> getAllBrands() {
        return ResponseEntity.ok(queryHandler.handle(new GetAllBrandsQuery()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查詢單一品牌")
    public ResponseEntity<Brand> getBrandById(@PathVariable Long id) {
        return queryHandler.handle(new GetBrandByIdQuery(id))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 更新品牌的會員點數規則
     * 僅限該品牌的管理員 (BRAND_ADMIN) 操作
     */
    @PutMapping("/point-config")
    @PreAuthorize("hasRole('BRAND_ADMIN')")
    @Operation(summary = "更新會員點數規則")
    @Audit(action = "UPDATE_POINT_CONFIG")
    public ResponseEntity<BrandPointConfig> updatePointConfig(
            @Valid @RequestBody UpdatePointConfigDto requestDto) {
        Long currentBrandId = helperService.getCurrentBrandId();
        BrandPointConfig updatedConfig = commandHandler.handle(new UpdatePointConfigCommand(currentBrandId, requestDto));
        return ResponseEntity.ok(updatedConfig);
    }
}