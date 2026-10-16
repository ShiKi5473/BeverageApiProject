package tw.niels.beverage_api_project.modules.product.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.modules.product.dto.CategoryResponseDto;
import tw.niels.beverage_api_project.modules.product.entity.CategoryEntity;
import tw.niels.beverage_api_project.modules.product.repository.CategoryJpaRepository;

/**
 * 分類讀取服務
 * <p>
 * 修改說明：
 * - 舊的 createCategory 已無呼叫端（建立分類改由 ProductCommandHandler 處理，含重名檢查），移除。
 * - 欄位名稱原與型別同名（CategoryJpaRepository CategoryJpaRepository，批次改名殘留），改為 categoryJpaRepository。
 * - getCategoriesByBrand 改為 @Transactional(readOnly = true)，在交易內轉成 DTO 並依排序權重排序（原 TODO）。
 * </p>
 */
@Service
public class CategoryService {

    private final CategoryJpaRepository categoryJpaRepository;

    public CategoryService(CategoryJpaRepository categoryJpaRepository) {
        this.categoryJpaRepository = categoryJpaRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponseDto> getCategoriesByBrand(Long brandId) {
        return categoryJpaRepository.findByBrand_Id(brandId).stream()
                // 依排序權重（null 排最後）再依 ID 排序，前端分類頁籤順序穩定
                .sorted(Comparator.comparing(CategoryEntity::getSortOrder,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CategoryEntity::getId))
                .map(CategoryResponseDto::fromEntity)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
