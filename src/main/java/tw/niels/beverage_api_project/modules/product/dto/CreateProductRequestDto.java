package tw.niels.beverage_api_project.modules.product.dto;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;

@Data
@Schema(description = "建立新商品請求")
public class CreateProductRequestDto {

    @NotBlank(message = "商品名不可為空")
    @Schema(description = "商品名稱", example = "珍珠奶茶")
    private String name;

    @Schema(description = "商品描述", example = "招牌波霸珍珠搭配香醇奶茶")
    private String description;

    @NotNull(message = "商品價格不可為空")
    @DecimalMin(value = "0.0", inclusive = false, message = "價格不可小於0")
    @Schema(description = "基本售價", example = "50.00")
    private BigDecimal basePrice;

    @Schema(description = "圖片連結 URL", example = "https://example.com/bubble-tea.jpg")
    private String imageUrl;

    @NotNull(message = "必須指定商品是否可用")
    @Schema(description = "上架狀態", example = "ACTIVE")
    private ProductStatus status;

    @NotEmpty(message = "必須為商品指定一個類別")
    @Schema(description = "所屬分類 ID 列表", example = "[1, 2]")
    private Set<Long> categoryIds;

    @Schema(description = "關聯的客製化選項群組 ID 列表", example = "[10, 11]")
    private Set<Long> optionGroupIds = new HashSet<>();

}
