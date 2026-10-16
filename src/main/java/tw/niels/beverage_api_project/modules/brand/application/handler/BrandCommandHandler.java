package tw.niels.beverage_api_project.modules.brand.application.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.application.command.*;
import tw.niels.beverage_api_project.modules.brand.domain.model.Brand;
import tw.niels.beverage_api_project.modules.brand.domain.model.BrandPointConfig;
import tw.niels.beverage_api_project.modules.brand.domain.repository.BrandRepository;

/**
 * ============================================================
 * Bounded Context: Brand & Store
 * Layer: Application - Command Handler
 * ============================================================
 *
 * 品牌相關命令處理器（Phase 3 重構完成版）
 *
 * 全部操作透過 {@link BrandRepository}（Domain 介面）操作 Domain Model，
 * 不再委派 BrandService，符合 DDD Application Layer 職責。
 *
 * 處理流程（以 UpdatePointConfig 為例）：
 * 1. 透過 BrandRepository 載入 Brand Aggregate
 * 2. 呼叫 brand.updatePointConfig() 更新積點配置 VO
 * 3. 儲存 Brand（BrandRepositoryImpl 負責同步 BrandPointConfigEntity）
 * ============================================================
 */
@Component
public class BrandCommandHandler {

    private final BrandRepository brandRepository;

    public BrandCommandHandler(BrandRepository brandRepository) {
        this.brandRepository = brandRepository;
    }

    /**
     * 更新品牌積點配置。
     *
     * 處理流程：
     * 1. 載入品牌 Domain Model（不存在拋出 ResourceNotFoundException）
     * 2. 建立新的 BrandPointConfig 值物件
     * 3. 呼叫 brand.updatePointConfig() 更新設定
     * 4. 儲存 Brand（BrandRepositoryImpl 同步更新 brand_point_configs 表）
     *
     * @param command 包含品牌 ID 與新積點設定 DTO
     */
    @Transactional
    public void handle(UpdatePointConfigCommand command) {
        Long brandId = command.brandId();
        var dto = command.requestDto();

        // 1. 載入品牌
        Brand brand = brandRepository.findById(brandId)
            .orElseThrow(() -> new ResourceNotFoundException("找不到品牌，ID：" + brandId));

        // 2. 建立新積點配置值物件並更新 Aggregate
        brand.updatePointConfig(new BrandPointConfig(
            dto.getEarnRate(),
            dto.getRedeemRate(),
            dto.getExpiryDays()
        ));

        // 3. 儲存（BrandRepositoryImpl 會同步寫入 BrandPointConfigEntity）
        brandRepository.save(brand);
    }
}
