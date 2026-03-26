package tw.niels.beverage_api_project.modules.product.dto;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductVariantEntity;

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

    @Schema(description = "商品規格列表 (新增)", example = "[{id:1, name:'大杯', price:60}]")
    private Set<ProductVariantDto> variants;

    @Schema(description = "選項群組 (POS 選單用)")
    private Set<OptionGroupResponseDto> optionGroups;

    @Schema(description = "所屬分類")
    private Set<CategoryBasicDto> categories;

    public static ProductPosDto fromEntity(ProductEntity ProductEntity) {
        if (ProductEntity == null) return null;
        ProductPosDto dto = new ProductPosDto();
        dto.setId(ProductEntity.getId());
        dto.setName(ProductEntity.getName());
        dto.setBasePrice(ProductEntity.getBasePrice());

        // 轉換規格資料
        if (ProductEntity.getVariants() != null) {
            dto.setVariants(ProductEntity.getVariants().stream()
                    .filter(v -> !v.isDeleted()) // 排除已刪除的規格
                    .map(ProductVariantDto::fromEntity)
                    .collect(Collectors.toSet()));
        }

        if (ProductEntity.getOptionGroups() != null) {
            dto.setOptionGroups(ProductEntity.getOptionGroups().stream().map(OptionGroupResponseDto::fromEntity).collect(Collectors.toSet()));
        }
        if (ProductEntity.getCategories() != null) {
            dto.setCategories(ProductEntity.getCategories().stream().map(CategoryBasicDto::fromEntity).collect(Collectors.toSet()));
        }
        return dto;
    }

    // 內部類別，用於傳輸簡單的規格資訊
    @Data
    public static class ProductVariantDto {
        private Long id;
        private String name;
        private BigDecimal price;

        public static ProductVariantDto fromEntity(ProductVariantEntity variant) {
            ProductVariantDto dto = new ProductVariantDto();
            dto.setId(variant.getId());
            dto.setName(variant.getName());
            dto.setPrice(variant.getPrice());
            return dto;
        }
    }
}