package tw.niels.beverage_api_project.modules.product.dto;

import java.math.BigDecimal;
import java.util.Set;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateProductRequestDto {
    @NotBlank(message="商品名不可為空")
    private String name;

    private String description;

    @NotBlank(message="商品價格不可為空")
    @DecimalMin(value="0.0", inclusive= false, message="價格不可小於0")
    private BigDecimal basePrice;

    private String imageUrl;

    @NotBlank(message="必須指定商品是否可用")
    private boolean isAvailable;

    @NotBlank(message="必須為商品指定一個類別")
    private Set<Long> categoryIds;

}
