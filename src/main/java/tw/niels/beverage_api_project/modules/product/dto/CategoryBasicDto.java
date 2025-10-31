package tw.niels.beverage_api_project.modules.product.dto;

import tw.niels.beverage_api_project.modules.product.entity.Category;

public class CategoryBasicDto {
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

    private Long categoryId;
    private String name;

    public static CategoryBasicDto fromEntity(Category category) {
        if (category == null)
            return null;
        CategoryBasicDto dto = new CategoryBasicDto();
        dto.setCategoryId(category.getCategoryId());
        dto.setName(category.getName());
        return dto;
    }
}
