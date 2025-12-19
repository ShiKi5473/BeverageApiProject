package tw.niels.beverage_api_project.modules.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 非同步訂單任務 DTO
 * 用於在 RabbitMQ 中傳遞建立訂單所需的資訊
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AsyncOrderTaskDto implements Serializable {

    // 用於冪等性檢查與追蹤 (Ticket ID)
    private String requestId;

    // 下單者資訊
    private Long brandId;
    private Long storeId;
    private Long userId; // 下單的使用者 (會員或店員)

    private String userPhone;

    // 原始訂單請求內容
    private CreateOrderRequestDto orderRequest;
}