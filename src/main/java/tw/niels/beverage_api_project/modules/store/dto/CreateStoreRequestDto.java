package tw.niels.beverage_api_project.modules.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "建立分店請求")
public class CreateStoreRequestDto {

    @NotNull(message = "品牌 ID 不可為空")
    @Schema(description = "所屬品牌 ID", example = "1")
    private Long brandId;

    @NotBlank(message = "店家名稱不可為空")
    @Schema(description = "分店名稱", example = "台北信義店")
    private String name;

    @Schema(description = "地址", example = "台北市信義區...")
    private String address;

    @Schema(description = "電話", example = "02-12345678")
    private String phone;

    public CreateStoreRequestDto(Long brandId, String name, String address, String phone) {
        this.brandId = brandId;
        this.name = name;
        this.address = address;
        this.phone = phone;
    }

    public CreateStoreRequestDto() {

    }

}