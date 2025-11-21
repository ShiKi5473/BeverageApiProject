package tw.niels.beverage_api_project.modules.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.niels.beverage_api_project.modules.store.entity.Store;

@Data
@NoArgsConstructor
@Schema(description = "分店資訊回應")
public class StoreResponseDto {

    @Schema(description = "分店 ID", example = "1")
    private Long storeId;

    @Schema(description = "分店名稱", example = "台北信義店")
    private String name;

    @Schema(description = "地址", example = "台北市信義區...")
    private String address;

    @Schema(description = "電話", example = "02-12345678")
    private String phoneNumber;

    // 靜態工廠方法：將 Entity 轉為 DTO
    public static StoreResponseDto fromEntity(Store store) {
        if (store == null) return null;

        StoreResponseDto dto = new StoreResponseDto();
        dto.setStoreId(store.getStoreId());
        dto.setName(store.getName());
        dto.setAddress(store.getAddress());
        dto.setPhoneNumber(store.getPhoneNumber());
        return dto;
    }
}