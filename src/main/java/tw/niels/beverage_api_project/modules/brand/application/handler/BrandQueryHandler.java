package tw.niels.beverage_api_project.modules.brand.application.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.modules.brand.application.query.*;
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.brand.service.BrandService;

import java.util.List;
import java.util.Optional;

/**
 * 品牌相關查詢處理器（唯讀）
 *
 * 處理流程：
 * 1. 接收查詢條件（全部品牌列表 或 單一品牌）
 * 2. 委派 BrandService 執行查詢
 * 3. 回傳 BrandEntity 實體列表或 Optional
 */
@Component
public class BrandQueryHandler {

    private final BrandService brandService;

    public BrandQueryHandler(BrandService brandService) {
        this.brandService = brandService;
    }

    @Transactional(readOnly = true)
    public List<BrandEntity> handle(GetAllBrandsQuery query) {
        return brandService.getAllBrandsForAdmin();
    }

    @Transactional(readOnly = true)
    public Optional<BrandEntity> handle(GetBrandByIdQuery query) {
        return brandService.getBrandById(query.brandId());
    }
}
