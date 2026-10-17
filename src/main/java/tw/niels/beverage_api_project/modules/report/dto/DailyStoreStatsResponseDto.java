package tw.niels.beverage_api_project.modules.report.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import tw.niels.beverage_api_project.modules.report.entity.DailyStoreStatsEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 分店日結統計回應 DTO。
 * <p>
 * Controller 不再直接回傳 {@link DailyStoreStatsEntity}（會把 JPA 實作細節如 Persistable 的 "new" 屬性
 * 一起序列化出去），並且所有 ID 序列化為字串，避免 TSID 超出 JS 安全整數範圍。
 * 欄位名稱沿用 Entity 的 JSON 名稱，前端 (report.js) 無須調整。
 * </p>
 */
@Schema(description = "分店日結統計 (回應)")
public record DailyStoreStatsResponseDto(
        @Schema(description = "統計資料 ID (字串)")
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,

        @Schema(description = "分店 ID (字串)")
        @JsonSerialize(using = ToStringSerializer.class)
        Long storeId,

        @Schema(description = "品牌 ID (字串)")
        @JsonSerialize(using = ToStringSerializer.class)
        Long brandId,

        @Schema(description = "統計日期", example = "2025-10-01")
        LocalDate date,

        @Schema(description = "已完成訂單數")
        Integer totalOrders,

        @Schema(description = "原始總額 (未扣折扣)")
        BigDecimal totalRevenue,

        @Schema(description = "折扣總額")
        BigDecimal totalDiscount,

        @Schema(description = "實收金額")
        BigDecimal finalRevenue,

        @Schema(description = "取消訂單數")
        Integer cancelledOrders,

        @Schema(description = "現金實收")
        BigDecimal cashTotal,

        @Schema(description = "LINE Pay 實收 (保留欄位，系統目前無此付款方式)")
        BigDecimal linePayTotal,

        @Schema(description = "信用卡實收")
        BigDecimal creditCardTotal,

        @Schema(description = "第三方支付實收")
        BigDecimal thirdPartyTotal,

        @Schema(description = "未指定付款方式的實收 (訂單沒有付款方式時)")
        BigDecimal unspecifiedTotal,

        @Schema(description = "建立時間")
        Instant createdAt
) {
    /** Entity → DTO 轉換 */
    public static DailyStoreStatsResponseDto from(DailyStoreStatsEntity e) {
        return new DailyStoreStatsResponseDto(
                e.getId(),
                e.getStoreId(),
                e.getBrandId(),
                e.getDate(),
                e.getTotalOrders(),
                e.getTotalRevenue(),
                e.getTotalDiscount(),
                e.getFinalRevenue(),
                e.getCancelledOrders(),
                e.getCashTotal(),
                e.getLinePayTotal(),
                e.getCreditCardTotal(),
                e.getThirdPartyTotal(),
                e.getUnspecifiedTotal(),
                e.getCreatedAt());
    }
}
