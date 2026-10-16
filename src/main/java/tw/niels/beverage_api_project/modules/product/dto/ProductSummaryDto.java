package tw.niels.beverage_api_project.modules.product.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@Schema(description = "商品列表摘要 (簡易版)")
public class ProductSummaryDto {

    // 修改（P0-10）：ID 以字串輸出
    @Schema(description = "商品 ID", example = "101", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "商品名稱", example = "珍珠奶茶")
    private String name;

    @Schema(description = "基本價格", example = "50.00")
    private BigDecimal basePrice;

    @Schema(description = "圖片 URL")
    private String imgUrl;

    @Schema(description = "商品描述")
    private String description;

    // 修改：以明確建構子取代 Lombok @AllArgsConstructor（避免與欄位上的 Jackson 註解在 IDE 編譯器產生誤判）
    public ProductSummaryDto(Long id, String name, BigDecimal basePrice, String imgUrl, String description) {
        this.id = id;
        this.name = name;
        this.basePrice = basePrice;
        this.imgUrl = imgUrl;
        this.description = description;
    }

    // 修改：參數名稱原與型別同名（批次改名殘留），改為 entity
    public static ProductSummaryDto fromEntity(ProductEntity entity) {
        if (entity == null) return null;
        return new ProductSummaryDto(
                entity.getId(),
                entity.getName(),
                entity.getBasePrice(),
                entity.getImageUrl(),
                entity.getDescription());
    }
}
