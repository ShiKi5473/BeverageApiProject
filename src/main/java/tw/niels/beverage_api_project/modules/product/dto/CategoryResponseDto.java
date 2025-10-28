package tw.niels.beverage_api_project.modules.product.dto;

import tw.niels.beverage_api_project.modules.product.entity.Category;

public class CategoryResponseDto {

    private Long categoryId;
    private String name;
    private Integer sortOrder;
    private Long brandId; // 也回傳所屬品牌 ID，方便前端使用

    /**
     * 靜態工廠方法，用於將 Category 實體轉換為 CategoryResponseDto。
     */
    public static CategoryResponseDto fromEntity(Category category) {
        if (category == null) {
            return null;
        }

        CategoryResponseDto dto = new CategoryResponseDto();
        dto.setCategoryId(category.getCategoryId());
        dto.setName(category.getName());
        dto.setSortOrder(category.getSortOrder());

        // 從關聯的 Brand 實體取得 brandId
        if (category.getBrand() != null) {
            dto.setBrandId(category.getBrand().getBrandId());
        }

        return dto;
    }

    // getter and setter

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getBrandId() {
        return brandId;
    }

    public void setBrandId(Long brandId) {
        this.brandId = brandId;
    }
}
