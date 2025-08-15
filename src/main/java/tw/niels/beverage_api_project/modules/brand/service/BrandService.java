package tw.niels.beverage_api_project.modules.brand.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.modules.brand.dto.CreateBrandRequestDto;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.repository.BrandRepository;

@Service
public class BrandService {

    @Autowired
    private BrandRepository brandRepository;

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

    public List<Brand> getAllBrands() {
        return brandRepository.findAll();
    }

    public Optional<Brand> getBrandById(Long id) {
        return brandRepository.findById(id);
    }
}
