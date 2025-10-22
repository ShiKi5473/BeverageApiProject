package tw.niels.beverage_api_project.modules.product.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateCategoryRequestDto {

    @NotBlank(message = "分類名稱不可為空")
    private String name;

    private Integer sortOrder; // 排序順序

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

}