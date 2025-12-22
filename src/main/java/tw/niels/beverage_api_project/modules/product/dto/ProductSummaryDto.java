package tw.niels.beverage_api_project.modules.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.niels.beverage_api_project.modules.product.entity.Product;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "商品列表摘要 (簡易版)")
public class ProductSummaryDto {

    @Schema(description = "商品 ID", example = "101")
    private Long id;

    @Schema(description = "商品名稱", example = "珍珠奶茶")
    private String name;

    @Schema(description = "基本價格", example = "50.00")
    private BigDecimal basePrice;

    @Schema(description = "圖片 URL")
    private String imgUrl;

    @Schema(description = "商品描述")
    private String description;

    public static ProductSummaryDto fromEntity(Product product) {
        if (product == null) return null;
        return new ProductSummaryDto(
                product.getId(),
                product.getName(),
                product.getBasePrice(),
                product.getImageUrl(),
                product.getDescription());
    }
}