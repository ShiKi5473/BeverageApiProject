package tw.niels.beverage_api_project.modules.brand.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.dto.CreateBrandRequestDto;
import tw.niels.beverage_api_project.modules.brand.dto.UpdatePointConfigDto;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.entity.BrandPointConfig;
import tw.niels.beverage_api_project.modules.brand.repository.BrandPointConfigRepository;
import tw.niels.beverage_api_project.modules.brand.repository.BrandRepository;

@Service
public class BrandService {

    private final BrandRepository brandRepository;
    private final BrandPointConfigRepository brandPointConfigRepository;

    public BrandService(BrandRepository brandRepository,
                        BrandPointConfigRepository brandPointConfigRepository) {
        this.brandRepository = brandRepository;
        this.brandPointConfigRepository = brandPointConfigRepository;
    }

    @Transactional // 建議加上交易註解
    public Brand createBrand(CreateBrandRequestDto requestDto) {
        // 檢查品牌名稱是否已存在
        brandRepository.findByName(requestDto.getName()).ifPresent(b -> {
            throw new IllegalStateException("品牌名稱 '" + requestDto.getName() + "' 已經存在。");
        });

        Brand brand = new Brand();
        brand.setName(requestDto.getName());
        brand.setContactPerson(requestDto.getContactPerson());
        brand.setActive(true); // 預設為啟用

        return brandRepository.save(brand);
    }

    @Transactional
    public BrandPointConfig updatePointConfig(Long brandId, UpdatePointConfigDto dto) {
        // 1. 確保品牌存在
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到品牌，ID：" + brandId));

        // 2. 直接從 config repository 查找 (因為 ID 是共享的，所以直接用 brandId)
        BrandPointConfig config = brandPointConfigRepository.findById(brandId)
                .orElse(null);

        // 3. 如果不存在則建立
        if (config == null) {
            config = new BrandPointConfig(brand);
        }

        // 4. 更新數值
        config.setEarnRate(dto.getEarnRate());
        config.setRedeemRate(dto.getRedeemRate());
        config.setExpiryDays(dto.getExpiryDays());

        // 5. 直接儲存 config
        return brandPointConfigRepository.save(config);
    }

    public List<Brand> getAllBrands() {
        return brandRepository.findAll();
    }

    public Optional<Brand> getBrandById(Long id) {
        return brandRepository.findById(id);
    }
}
