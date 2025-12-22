package tw.niels.beverage_api_project.modules.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tw.niels.beverage_api_project.modules.product.entity.OptionGroup;
import tw.niels.beverage_api_project.modules.product.enums.SelectionType;

import java.util.Set;
import java.util.stream.Collectors;

@Data
@Schema(description = "客製化選項群組 (如: 甜度)")
public class OptionGroupResponseDto {

    @Schema(description = "群組 ID", example = "5")
    private Long groupId;

    @Schema(description = "群組名稱", example = "甜度")
    private String name;

    @Schema(description = "選擇類型 (SINGLE: 單選, MULTIPLE: 多選)", example = "SINGLE")
    private SelectionType selectionType;

    @Schema(description = "排序權重", example = "1")
    private Integer sortOrder;

    @Schema(description = "包含的選項列表")
    private Set<ProductOptionResponseDto> options;

    public static OptionGroupResponseDto fromEntity(OptionGroup entity) {
        if (entity == null) return null;

        OptionGroupResponseDto dto = new OptionGroupResponseDto();
        dto.setGroupId(entity.getId());
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
}