package tw.niels.beverage_api_project.modules.product.dto;

import java.util.Set;
import java.util.stream.Collectors;

import tw.niels.beverage_api_project.modules.product.entity.OptionGroup;
import tw.niels.beverage_api_project.modules.product.enums.SelectionType;

public class OptionGroupResponseDto {
    private Long groupId;
    private String name;
    private SelectionType selectionType;
    private Integer sortOrder;
    private Set<ProductOptionResponseDto> options;

    public static OptionGroupResponseDto fromEntity(OptionGroup entity) {
        OptionGroupResponseDto dto = new OptionGroupResponseDto();
        dto.setGroupId(entity.getGroupId());
        dto.setName(entity.getName());
        dto.setSelectionType(entity.getSelectionType());
        dto.setSortOrder(entity.getSortOrder());

        if (entity.getOptions() != null) {
            dto.setOptions(entity.getOptions().stream()
                    .map(ProductOptionResponseDto::fromEntity)
                    .collect(Collectors.toSet()));
        }
        return dto;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

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

    public Set getOptions() {
        return options;
    }

    public void setOptions(Set options) {
        this.options = options;
    }
}
