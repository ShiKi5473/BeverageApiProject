package tw.niels.beverage_api_project.modules.store.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.brand.repository.BrandJpaRepository;
import tw.niels.beverage_api_project.modules.store.dto.CreateStoreRequestDto;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;
import tw.niels.beverage_api_project.modules.store.repository.StoreJpaRepository;

@Service
public class StoreService {
    private final StoreJpaRepository storeRepository;
    private final BrandJpaRepository brandRepository;

    public StoreService(StoreJpaRepository storeRepository, BrandJpaRepository brandRepository) {
        this.storeRepository = storeRepository;
        this.brandRepository = brandRepository;
    }

    /**
     * 依品牌 ID 與店家 ID 查詢店家，找不到則拋出例外。
     * 供跨模組的 Application Service 使用，避免直接依賴 StoreJpaRepository。
     */
    @Transactional(readOnly = true)
    public StoreEntity getStore(Long brandId, Long storeId) {
        return storeRepository.findByBrand_IdAndId(brandId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到店家 (ID: " + storeId + ")"));
    }

    @Transactional
    public StoreEntity createStore(CreateStoreRequestDto requestDto) {
        BrandEntity BrandEntity = brandRepository.findById(requestDto.getBrandId())
                .orElseThrow(() -> new ResourceNotFoundException("找不到品牌，ID：" + requestDto.getBrandId()));

        StoreEntity newStore = new StoreEntity();
        newStore.setBrand(BrandEntity);
        newStore.setName(requestDto.getName());
        newStore.setAddress(requestDto.getAddress());
        newStore.setPhoneNumber(requestDto.getPhone());

        return storeRepository.save(newStore);
    }
}
