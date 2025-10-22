package tw.niels.beverage_api_project.modules.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateStoreRequestDto {

    @NotNull(message = "品牌 ID 不可為空")
    private Long brandId;

    @NotBlank(message = "店家名稱不可為空")
    private String name;

    private String address;

    private String phone;
}