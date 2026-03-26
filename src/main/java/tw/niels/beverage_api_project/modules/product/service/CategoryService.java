package tw.niels.beverage_api_project.modules.product.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.brand.repository.BrandJpaRepository;
import tw.niels.beverage_api_project.modules.product.dto.CreateCategoryRequestDto;
import tw.niels.beverage_api_project.modules.product.entity.CategoryEntity;
import tw.niels.beverage_api_project.modules.product.repository.CategoryJpaRepository;

@Service
public class CategoryService {

    private final CategoryJpaRepository CategoryJpaRepository;
    private final BrandJpaRepository brandRepository;

    public CategoryService(CategoryJpaRepository CategoryJpaRepository, BrandJpaRepository brandRepository) {
        this.CategoryJpaRepository = CategoryJpaRepository;
        this.brandRepository = brandRepository;
    }

    @Transactional
    public CategoryEntity createCategory(Long brandId, CreateCategoryRequestDto requestDto) {
        // 1. 驗證品牌是否存在
        BrandEntity BrandEntity = brandRepository.findById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到品牌，ID：" + brandId));

        // 2. 檢查同一品牌下是否已有相同名稱的分類 (忽略大小寫)
        CategoryJpaRepository.findByBrand_IdAndNameIgnoreCase(brandId, requestDto.getName())
                .ifPresent(existingCategory -> {
                    throw new BadRequestException(
                            "品牌 '" + BrandEntity.getName() + "' 下已存在名為 '" + requestDto.getName() + "' 的分類。");
                });

        // 3. 建立新的 CategoryEntity 實體
        CategoryEntity newCategory = new CategoryEntity();
        newCategory.setBrand(BrandEntity);
        newCategory.setName(requestDto.getName());
        newCategory.setSortOrder(requestDto.getSortOrder() != null ? requestDto.getSortOrder() : 0); // 提供預設值

        // 4. 儲存到資料庫並回傳
        return CategoryJpaRepository.save(newCategory);
    }

    @Transactional
    public List<CategoryEntity> getCategoriesByBrand(Long brandId) {
        return CategoryJpaRepository.findByBrand_Id(brandId);
        // TODO 未來可加入排序
    }

}