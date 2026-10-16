package tw.niels.beverage_api_project.modules.product.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tw.niels.beverage_api_project.modules.product.entity.OptionGroupEntity;
import tw.niels.beverage_api_project.modules.product.enums.SelectionType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Schema(description = "客製化選項群組 (如: 甜度)")
public class OptionGroupResponseDto {

    // 修改（P0-10）：ID 以字串輸出
    @Schema(description = "群組 ID", example = "5", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long groupId;

    @Schema(description = "群組名稱", example = "甜度")
    private String name;

    @Schema(description = "選擇類型 (SINGLE: 單選, MULTIPLE: 多選)", example = "SINGLE")
    private SelectionType selectionType;

    @Schema(description = "排序權重", example = "1")
    private Integer sortOrder;

    /**
     * 修改：Set → List（JSON 一樣是陣列，欄位名稱不變）。
     * 1. 使用可變的 ArrayList，Redis 快取（GenericJackson2JsonRedisSerializer）可正確反序列化；
     * 2. 依選項 ID（TSID，約等於建立順序）排序，畫面上的選項順序穩定，不再隨 HashSet 亂跳。
     */
    @Schema(description = "包含的選項列表")
    private List<ProductOptionResponseDto> options = new ArrayList<>();

    public static OptionGroupResponseDto fromEntity(OptionGroupEntity entity) {
        if (entity == null) return null;

        OptionGroupResponseDto dto = new OptionGroupResponseDto();
        dto.setGroupId(entity.getId());
        dto.setName(entity.getName());
        dto.setSelectionType(entity.getSelectionType());
        dto.setSortOrder(entity.getSortOrder());

        if (entity.getOptions() != null) {
            dto.setOptions(entity.getOptions().stream()
                    .map(ProductOptionResponseDto::fromEntity)
                    .sorted(Comparator.comparing(ProductOptionResponseDto::getOptionId,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toCollection(ArrayList::new)));
        }
        return dto;
    }
}
