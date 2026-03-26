package tw.niels.beverage_api_project.modules.brand.application.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.modules.brand.application.command.*;
import tw.niels.beverage_api_project.modules.brand.entity.BrandPointConfigEntity;
import tw.niels.beverage_api_project.modules.brand.service.BrandService;

/**
 * 品牌相關命令處理器
 *
 * 處理流程（UpdatePointConfig）：
 * 1. 接收 UpdatePointConfigCommand（含 brandId 與積點設定 DTO）
 * 2. 委派 BrandService 更新積點規則（累積率、折抵率）
 * 3. 回傳更新後的 BrandPointConfigEntity
 */
@Component
public class BrandCommandHandler {

    private final BrandService brandService;

    public BrandCommandHandler(BrandService brandService) {
        this.brandService = brandService;
    }

    @Transactional
    public BrandPointConfigEntity handle(UpdatePointConfigCommand command) {
        return brandService.updatePointConfig(command.brandId(), command.requestDto());
    }
}
