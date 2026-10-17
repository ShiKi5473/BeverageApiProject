package tw.niels.beverage_api_project.modules.brand.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;

import java.util.Date;

@Data
@Schema(description = "品牌回應資料")
public class BrandResponseDto {

    @Schema(description = "品牌 ID", example = "1")
    // ID 以字串輸出，避免 TSID 超過 JS 安全整數範圍而失真(P0-10)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long brandId;

    @Schema(description = "品牌名稱", example = "品茶軒")
    private String name;

    @Schema(description = "聯絡人", example = "張老闆")
    private String contactPerson;

    @Schema(description = "是否啟用", example = "true")
    private Boolean isActive;

    @Schema(description = "建立時間")
    private Date createdAt;

    @Schema(description = "更新時間")
    private Date updatedAt;

    // 清理(P3)：參數原本與型別同名（BrandEntity BrandEntity），改為 brand
    public static BrandResponseDto fromEntity(BrandEntity brand) {
        if (brand == null) return null;
        BrandResponseDto dto = new BrandResponseDto();
        dto.setBrandId(brand.getId());
        dto.setName(brand.getName());
        dto.setContactPerson(brand.getContactPerson());
        dto.setIsActive(brand.getIsActive());
        dto.setCreatedAt(brand.getCreatedAt());
        dto.setUpdatedAt(brand.getUpdatedAt());
        return dto;
    }
}