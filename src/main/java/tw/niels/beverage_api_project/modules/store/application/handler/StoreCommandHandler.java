package tw.niels.beverage_api_project.modules.store.application.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.domain.repository.BrandRepository;
import tw.niels.beverage_api_project.modules.store.application.command.CreateStoreCommand;
import tw.niels.beverage_api_project.modules.store.domain.model.Store;
import tw.niels.beverage_api_project.modules.store.domain.repository.StoreRepository;

/**
 * ============================================================
 * Bounded Context: Brand & Store
 * Layer: Application - Command Handler
 * ============================================================
 *
 * 分店相關命令處理器（Phase 3 重構完成版）
 *
 * 全部操作透過 {@link StoreRepository}（Domain 介面）操作 Domain Model，
 * 不再委派 StoreService，符合 DDD Application Layer 職責。
 *
 * 處理流程（CreateStore）：
 * 1. 驗證品牌存在（BrandRepository）
 * 2. 建立 Store Domain Model
 * 3. 透過 StoreRepository 持久化
 * 4. 回傳新建分店 ID
 * ============================================================
 */
@Component
public class StoreCommandHandler {

    private final BrandRepository brandRepository;
    private final StoreRepository storeRepository;

    public StoreCommandHandler(BrandRepository brandRepository, StoreRepository storeRepository) {
        this.brandRepository = brandRepository;
        this.storeRepository = storeRepository;
    }

    /**
     * 建立分店。
     *
     * 處理流程：
     * 1. 驗證品牌存在（不存在拋出 ResourceNotFoundException）
     * 2. 建立 Store Domain Model（含名稱、地址、電話，isActive 預設 true）
     * 3. 透過 StoreRepository 持久化（StoreMapper 轉換，Brand 以 Proxy 設定）
     * 4. 回傳新建分店 ID
     *
     * @param command 包含品牌 ID 與分店建立資料
     * @return 新建分店的 ID
     */
    @Transactional
    public Long handle(CreateStoreCommand command) {
        Long brandId = command.brandId();
        var req = command.requestDto();

        // 1. 驗證品牌存在
        brandRepository.findById(brandId)
            .orElseThrow(() -> new ResourceNotFoundException("找不到品牌，ID：" + brandId));

        // 2. 建立 Store Domain Model
        Store store = new Store(null, brandId, req.getName(), req.getAddress(), req.getPhone(), true);

        // 3. 持久化（StoreRepositoryImpl 負責 ID 同步回 Domain Model）
        storeRepository.save(store);

        // 4. 回傳新建分店 ID
        return store.getId();
    }
}
