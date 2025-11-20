package tw.niels.beverage_api_project.modules.brand.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class UpdatePointConfigDto {

    @NotNull(message = "累積匯率不可為空")
    @DecimalMin(value = "0.01", message = "累積匯率必須大於 0")
    private BigDecimal earnRate; // 例如：30 (每30元1點)

    @NotNull(message = "折抵匯率不可為空")
    @DecimalMin(value = "0.01", message = "折抵匯率必須大於 0")
    private BigDecimal redeemRate; // 例如：0.1 (1點折0.1元，即10點折1元)

    private Integer expiryDays; // 可選

    // Getters and Setters
    public BigDecimal getEarnRate() { return earnRate; }
    public void setEarnRate(BigDecimal earnRate) { this.earnRate = earnRate; }
    public BigDecimal getRedeemRate() { return redeemRate; }
    public void setRedeemRate(BigDecimal redeemRate) { this.redeemRate = redeemRate; }
    public Integer getExpiryDays() { return expiryDays; }
    public void setExpiryDays(Integer expiryDays) { this.expiryDays = expiryDays; }
}