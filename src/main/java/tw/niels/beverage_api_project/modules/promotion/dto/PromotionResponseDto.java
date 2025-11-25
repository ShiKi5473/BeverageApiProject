package tw.niels.beverage_api_project.modules.promotion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tw.niels.beverage_api_project.modules.product.dto.ProductSummaryDto;
import tw.niels.beverage_api_project.modules.promotion.entity.Promotion;
import tw.niels.beverage_api_project.modules.promotion.enums.PromotionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@Schema(description = "促銷活動回應資料")
public class PromotionResponseDto {

    private Long promotionId;
    private String name;
    private String description;
    private PromotionType type;
    private BigDecimal value;
    private BigDecimal minSpend;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private boolean isActive;

    // 使用 Set 確保不重複 (對應 Entity 的 ManyToMany Set)
    private Set<ProductSummaryDto> applicableProducts;

    public static PromotionResponseDto fromEntity(Promotion entity) {
        if (entity == null) return null; // 防呆

        PromotionResponseDto dto = new PromotionResponseDto();
        dto.setPromotionId(entity.getPromotionId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setType(entity.getType());
        dto.setValue(entity.getValue());
        dto.setMinSpend(entity.getMinSpend());
        dto.setStartDate(entity.getStartDate());
        dto.setEndDate(entity.getEndDate());
        dto.setActive(entity.getActive());

        // 【修正 2】改用明確的 Lambda 表達式，並確保 null check
        if (entity.getApplicableProducts() != null) {
            Set<ProductSummaryDto> products = entity.getApplicableProducts().stream()
                    .map(product -> ProductSummaryDto.fromEntity(product)) // 明確 Lambda
                    .collect(Collectors.toSet());
            dto.setApplicableProducts(products);
        }

        return dto;
    }
}