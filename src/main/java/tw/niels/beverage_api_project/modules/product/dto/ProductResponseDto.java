package tw.niels.beverage_api_project.modules.product.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;

@Data
@Schema(description = "商品詳細資訊回應")
public class ProductResponseDto {

    // 修改（P0-10）：ID 以字串輸出
    @Schema(description = "商品 ID", example = "101", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long productId;

    @Schema(description = "商品名稱", example = "珍珠奶茶")
    private String name;

    @Schema(description = "商品描述")
    private String description;

    @Schema(description = "基本價格", example = "50.00")
    private BigDecimal basePrice;

    @Schema(description = "圖片 URL")
    private String imageUrl;

    @Schema(description = "商品狀態", example = "ACTIVE")
    private ProductStatus status;

    // 新增：建立商品後回傳規格（含系統產生的規格 ID），前端不必再查一次
    @Schema(description = "有效規格列表（依價格排序）")
    private List<ProductPosDto.ProductVariantDto> variants = new ArrayList<>();

    // 修改：Set → 可變 List（JSON 仍為陣列、欄位名稱不變）
    @Schema(description = "所屬分類列表")
    private List<CategoryBasicDto> categories = new ArrayList<>();

    @Schema(description = "關聯的客製化選項群組")
    private List<OptionGroupResponseDto> optionGroups = new ArrayList<>();

    // 修改：參數名稱原與型別同名（批次改名殘留），改為 entity
    public static ProductResponseDto fromEntity(ProductEntity entity) {
        if (entity == null) return null;
        ProductResponseDto dto = new ProductResponseDto();
        dto.setProductId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setBasePrice(entity.getBasePrice());
        dto.setImageUrl(entity.getImageUrl());
        dto.setStatus(entity.getStatus());

        if (entity.getVariants() != null) {
            dto.setVariants(entity.getVariants().stream()
                    .filter(v -> !v.isDeleted())
                    .map(ProductPosDto.ProductVariantDto::fromEntity)
                    .sorted(Comparator.comparing(ProductPosDto.ProductVariantDto::getPrice,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toCollection(ArrayList::new)));
        }

        if (entity.getCategories() != null) {
            dto.setCategories(entity.getCategories().stream()
                    .map(CategoryBasicDto::fromEntity)
                    .collect(Collectors.toCollection(ArrayList::new)));
        }

        if (entity.getOptionGroups() != null) {
            dto.setOptionGroups(entity.getOptionGroups().stream()
                    .map(OptionGroupResponseDto::fromEntity)
                    .collect(Collectors.toCollection(ArrayList::new)));
        }
        return dto;
    }
}
