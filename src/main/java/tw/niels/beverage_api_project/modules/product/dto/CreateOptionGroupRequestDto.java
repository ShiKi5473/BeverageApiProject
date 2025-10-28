package tw.niels.beverage_api_project.modules.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tw.niels.beverage_api_project.modules.product.enums.SelectionType;

public class CreateOptionGroupRequestDto {
    @NotBlank(message = "選項群組名稱不可為空")
    private String name;

    @NotNull(message = "必須指定單選或多選")
    private SelectionType selectionType;

    private Integer sortOrder = 0;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SelectionType getSelectionType() {
        return selectionType;
    }

    public void setSelectionType(SelectionType selectionType) {
        this.selectionType = selectionType;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
