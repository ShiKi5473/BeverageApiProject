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
import lombok.NoArgsConstructor;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductVariantEntity;

/**
 * POS／線上點餐用的商品資料。
 * <p>
 * 修改說明：
 * - P0-10：所有 ID 以字串輸出（ToStringSerializer），避免 TSID 在前端精度遺失。
 * - 依 API 契約補上 imageUrl。
 * - 集合由 Set 改為可變的 ArrayList（JSON 仍為陣列、欄位名稱不變）：
 *   此 DTO 會存入 Redis（GenericJackson2JsonRedisSerializer），必須是可被 Jackson 反序列化的可變集合；
 *   同時排序（規格依價格、群組依排序權重、分類依排序權重），前端顯示順序穩定。
 * </p>
 */
@Data
@NoArgsConstructor
@Schema(description = "POS 端商品資料 (包含選項結構)")
public class ProductPosDto {

    @Schema(description = "商品 ID", example = "101", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "商品名稱", example = "珍珠奶茶")
    private String name;

    @Schema(description = "基本價格", example = "50.00")
    private BigDecimal basePrice;

    // 新增：依契約補上商品圖片網址
    @Schema(description = "圖片 URL")
    private String imageUrl;

    @Schema(description = "商品規格列表 (已排除軟刪除)", example = "[{id:'1', name:'大杯', price:60}]")
    private List<ProductVariantDto> variants = new ArrayList<>();

    @Schema(description = "選項群組 (POS 選單用)")
    private List<OptionGroupResponseDto> optionGroups = new ArrayList<>();

    @Schema(description = "所屬分類")
    private List<CategoryBasicDto> categories = new ArrayList<>();

    // 修改：參數名稱原與型別同名（批次改名殘留），改為 entity
    public static ProductPosDto fromEntity(ProductEntity entity) {
        if (entity == null) return null;
        ProductPosDto dto = new ProductPosDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setBasePrice(entity.getBasePrice());
        dto.setImageUrl(entity.getImageUrl());

        // 轉換規格資料（排除已軟刪除的規格），依價格由低到高排序
        if (entity.getVariants() != null) {
            dto.setVariants(entity.getVariants().stream()
                    .filter(v -> !v.isDeleted())
                    .map(ProductVariantDto::fromEntity)
                    .sorted(Comparator.comparing(ProductVariantDto::getPrice,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toCollection(ArrayList::new)));
        }

        // 選項群組依排序權重、再依 ID 排序
        if (entity.getOptionGroups() != null) {
            dto.setOptionGroups(entity.getOptionGroups().stream()
                    .map(OptionGroupResponseDto::fromEntity)
                    .sorted(Comparator.comparing(OptionGroupResponseDto::getSortOrder,
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(OptionGroupResponseDto::getGroupId,
                                    Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toCollection(ArrayList::new)));
        }

        if (entity.getCategories() != null) {
            dto.setCategories(entity.getCategories().stream()
                    .map(CategoryBasicDto::fromEntity)
                    .sorted(Comparator.comparing(CategoryBasicDto::getCategoryId,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toCollection(ArrayList::new)));
        }
        return dto;
    }

    /** 內部類別，用於傳輸簡單的規格資訊 */
    @Data
    @NoArgsConstructor
    public static class ProductVariantDto {
        // 修改（P0-10）：規格 ID 以字串輸出（下單時前端需原樣送回 variantId）
        @Schema(description = "規格 ID", type = "string")
        @JsonSerialize(using = ToStringSerializer.class)
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
