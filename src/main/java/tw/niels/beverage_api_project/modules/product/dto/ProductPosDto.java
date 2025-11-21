package tw.niels.beverage_api_project.modules.product.dto;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.niels.beverage_api_project.modules.product.entity.Product;

@Data
@NoArgsConstructor
@Schema(description = "POS 端商品資料 (包含選項結構)")
public class ProductPosDto {
    @Schema(description = "商品 ID", example = "101")
    private Long id;

    @Schema(description = "商品名稱", example = "珍珠奶茶")
    private String name;

    @Schema(description = "基本價格", example = "50.00")
    private BigDecimal basePrice;

    @Schema(description = "選項群組 (POS 選單用)")
    private Set<OptionGroupResponseDto> optionGroups;

    @Schema(description = "所屬分類")
    private Set<CategoryBasicDto> categories;

    public static ProductPosDto fromEntity(Product product) {
        if (product == null) return null;
        ProductPosDto dto = new ProductPosDto();
        dto.setId(product.getProductId());
        dto.setName(product.getName());
        dto.setBasePrice(product.getBasePrice());
        if (product.getOptionGroups() != null) {
            dto.setOptionGroups(product.getOptionGroups().stream().map(OptionGroupResponseDto::fromEntity).collect(Collectors.toSet()));
        }
        if (product.getCategories() != null) {
            dto.setCategories(product.getCategories().stream().map(CategoryBasicDto::fromEntity).collect(Collectors.toSet()));
        }
        return dto;
    }
}
