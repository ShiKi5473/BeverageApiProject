package tw.niels.beverage_api_project.modules.product.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tw.niels.beverage_api_project.modules.product.entity.ProductOptionEntity;

import java.math.BigDecimal;

@Data
@Schema(description = "客製化選項項目 (如: 半糖)")
public class ProductOptionResponseDto {

    // 修改（P0-10）：ID 以字串輸出
    @Schema(description = "選項 ID", example = "22", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long optionId;

    @Schema(description = "選項名稱", example = "半糖")
    private String optionName;

    @Schema(description = "價格調整 (正數加價)", example = "0.00")
    private BigDecimal priceAdjustment;

    @Schema(description = "是否為預設值", example = "false")
    private boolean isDefault;

    /**
     * 修改：Lombok 對 boolean isDefault 產生的是 isDefault()／setDefault()，
     * Jackson 會把屬性名推成 "default"，與 API 契約的 "isDefault" 不符。
     * 改為明確的存取方法並以 @JsonProperty 固定名稱（序列化與 Redis 快取反序列化都使用同一名稱）。
     */
    @JsonProperty("isDefault")
    public boolean isDefault() {
        return isDefault;
    }

    @JsonProperty("isDefault")
    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public static ProductOptionResponseDto fromEntity(ProductOptionEntity entity) {
        if (entity == null) return null;

        ProductOptionResponseDto dto = new ProductOptionResponseDto();
        dto.setOptionId(entity.getId());
        dto.setOptionName(entity.getOptionName());
        dto.setPriceAdjustment(entity.getPriceAdjustment());
        dto.setDefault(entity.isDefault());
        return dto;
    }
}
