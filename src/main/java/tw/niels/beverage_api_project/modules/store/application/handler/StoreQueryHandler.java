package tw.niels.beverage_api_project.modules.store.application.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.modules.store.application.query.GetStoresByBrandQuery;
import tw.niels.beverage_api_project.modules.store.dto.StoreResponseDto;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 分店相關查詢處理器
 *
 * 處理流程：
 * 1. 接收 GetStoresByBrandQuery
 * 2. 透過 StoreRepository 查詢品牌下所有分店
 * 3. 轉換為 StoreResponseDto 回傳
 *
 * 備註：過渡期直接使用 JPA StoreRepository，待 Phase 3 改用 Domain Repository。
 */
@Component
public class StoreQueryHandler {

    private final StoreRepository storeRepository;

    public StoreQueryHandler(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    /**
     * 查詢品牌下所有分店
     *
     * 處理流程：
     * 1. 依 brandId 查詢分店列表
     * 2. 將 Entity 轉為 DTO
     */
    @Transactional(readOnly = true)
    public List<StoreResponseDto> handle(GetStoresByBrandQuery query) {
        List<Store> stores = storeRepository.findByBrand_Id(query.brandId());
        return stores.stream()
            .map(StoreResponseDto::fromEntity)
            .collect(Collectors.toList());
    }
}
