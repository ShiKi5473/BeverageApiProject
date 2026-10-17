package tw.niels.beverage_api_project.modules.store.application.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.modules.store.application.query.GetStoresByBrandQuery;
import tw.niels.beverage_api_project.modules.store.dto.StoreResponseDto;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;
import tw.niels.beverage_api_project.modules.store.repository.StoreJpaRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 分店相關查詢處理器
 *
 * 處理流程：
 * 1. 接收 GetStoresByBrandQuery
 * 2. 透過 StoreJpaRepository 查詢品牌下所有分店
 * 3. 轉換為 StoreResponseDto 回傳
 *
 * 備註：過渡期直接使用 JPA StoreJpaRepository，待 Phase 3 改用 Domain Repository。
 */
@Component
public class StoreQueryHandler {

    private final StoreJpaRepository storeRepository;

    public StoreQueryHandler(StoreJpaRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    /**
     * 查詢品牌下所有分店
     *
     * 處理流程：
     * 1. 依 brandId 查詢分店列表（一律帶 brandId，只會回傳自己品牌的分店）
     * 2. onlyActive = true 時（會員／訪客）只取營業中的分店
     * 3. 將 Entity 轉為 DTO（只含公開欄位）
     */
    @Transactional(readOnly = true)
    public List<StoreResponseDto> handle(GetStoresByBrandQuery query) {
        List<StoreEntity> stores = query.onlyActive()
            ? storeRepository.findByBrand_IdAndIsActiveTrue(query.brandId())
            : storeRepository.findByBrand_Id(query.brandId());
        return stores.stream()
            .map(StoreResponseDto::fromEntity)
            .collect(Collectors.toList());
    }
}
