package tw.niels.beverage_api_project.modules.brand.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;

import java.util.Date;

@Data
@Schema(description = "品牌回應資料")
public class BrandResponseDto {

    @Schema(description = "品牌 ID", example = "1")
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

    public static BrandResponseDto fromEntity(Brand brand) {
        if (brand == null) return null;
        BrandResponseDto dto = new BrandResponseDto();
        dto.setBrandId(brand.getBrandId());
        dto.setName(brand.getName());
        dto.setContactPerson(brand.getContactPerson());
        dto.setIsActive(brand.getActive());
        dto.setCreatedAt(brand.getCreatedAt());
        dto.setUpdatedAt(brand.getUpdatedAt());
        return dto;
    }
}