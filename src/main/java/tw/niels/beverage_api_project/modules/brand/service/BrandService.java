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
import tw.niels.beverage_api_project.modules.brand.repository.BrandRepository;

@Service
public class BrandService {

    private final BrandRepository brandRepository;

    public BrandService(BrandRepository brandRepository) {
        this.brandRepository = brandRepository;
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
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到品牌，ID：" + brandId));

        BrandPointConfig config = brand.getPointConfig();

        // 如果還沒有設定檔，就建立一個新的
        if (config == null) {
            config = new BrandPointConfig(brand);
            brand.setPointConfig(config); // 維護雙向關聯
        }

        // 更新數值
        config.setEarnRate(dto.getEarnRate());
        config.setRedeemRate(dto.getRedeemRate());
        config.setExpiryDays(dto.getExpiryDays());


        brandRepository.save(brand);

        return config;
    }

    public List<Brand> getAllBrands() {
        return brandRepository.findAll();
    }

    public Optional<Brand> getBrandById(Long id) {
        return brandRepository.findById(id);
    }
}
