package tw.niels.beverage_api_project.modules.brand.application.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.modules.brand.application.query.*;
import tw.niels.beverage_api_project.modules.brand.dto.BrandResponseDto;
import tw.niels.beverage_api_project.modules.brand.service.BrandService;

import java.util.List;
import java.util.Optional;

/**
 * 品牌相關查詢處理器（唯讀）
 *
 * 處理流程：
 * 1. 接收查詢條件（全部品牌列表 或 單一品牌）
 * 2. 委派 BrandService 執行查詢
 * 3. 轉為 BrandResponseDto 後回傳列表或 Optional（不暴露 JPA Entity）
 */
@Component
public class BrandQueryHandler {

    private final BrandService brandService;

    public BrandQueryHandler(BrandService brandService) {
        this.brandService = brandService;
    }

    // 改為回傳 BrandResponseDto：Controller 不再暴露 JPA Entity，且 ID 以字串輸出
    @Transactional(readOnly = true)
    public List<BrandResponseDto> handle(GetAllBrandsQuery query) {
        return brandService.getAllBrandsForAdmin().stream()
                .map(BrandResponseDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<BrandResponseDto> handle(GetBrandByIdQuery query) {
        return brandService.getBrandById(query.brandId()).map(BrandResponseDto::fromEntity);
    }
}
