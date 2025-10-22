package tw.niels.beverage_api_project.modules.store.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.repository.BrandRepository;
import tw.niels.beverage_api_project.modules.store.dto.CreateStoreRequestDto;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;

@Service
public class StoreService {
    private final StoreRepository storeRepository;
    private final BrandRepository brandRepository;

    public StoreService(StoreRepository storeRepository, BrandRepository brandRepository) {
        this.storeRepository = storeRepository;
        this.brandRepository = brandRepository;
    }

    @Transactional
    public Store createStore(CreateStoreRequestDto requestDto) {
        Brand brand = brandRepository.findById(requestDto.getBrandId())
                .orElseThrow(() -> new ResourceNotFoundException("找不到品牌，ID：" + requestDto.getBrandId()));

        Store newStore = new Store();
        newStore.setBrand(brand);
        newStore.setName(requestDto.getName());
        newStore.setAddress(requestDto.getAddress());
        newStore.setPhoneNumber(requestDto.getPhone());

        return storeRepository.save(newStore);
    }
}
