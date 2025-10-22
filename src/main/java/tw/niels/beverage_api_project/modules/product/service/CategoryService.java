package tw.niels.beverage_api_project.modules.product.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.repository.BrandRepository;
import tw.niels.beverage_api_project.modules.product.dto.CreateCategoryRequestDto;
import tw.niels.beverage_api_project.modules.product.entity.Category;
import tw.niels.beverage_api_project.modules.product.repository.CategoryRepository;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    public CategoryService(CategoryRepository categoryRepository, BrandRepository brandRepository) {
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
    }

    @Transactional
    public Category createCategory(Long brandId, CreateCategoryRequestDto requestDto) {
        // 1. 驗證品牌是否存在
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到品牌，ID：" + brandId));

        // 2. 檢查同一品牌下是否已有相同名稱的分類 (忽略大小寫)
        categoryRepository.findByBrand_BrandIdAndNameIgnoreCase(brandId, requestDto.getName())
                .ifPresent(existingCategory -> {
                    throw new BadRequestException(
                            "品牌 '" + brand.getName() + "' 下已存在名為 '" + requestDto.getName() + "' 的分類。");
                });

        // 3. 建立新的 Category 實體
        Category newCategory = new Category();
        newCategory.setBrand(brand);
        newCategory.setName(requestDto.getName());
        newCategory.setSortOrder(requestDto.getSortOrder() != null ? requestDto.getSortOrder() : 0); // 提供預設值

        // 4. 儲存到資料庫並回傳
        return categoryRepository.save(newCategory);
    }

}