package tw.niels.beverage_api_project.modules.inventory.domain.model;

import tw.niels.beverage_api_project.common.domain.vo.Money;
import java.util.Objects;

/**
 * ============================================================
 * Bounded Context: Inventory
 * Layer: Domain - Aggregate Root
 * ============================================================
 *
 * 庫存品項聚合根
 *
 * 管理單一品項（如：牛奶、茉莉綠茶、白砂糖）的基本定義與全域安全水位。
 *
 * 業務規則：
 * - 必須屬於一個品牌。
 * - 擁有當前總存量（totalQuantity）。
 * - 擁有安全存量（safetyStock），低於此值時建議補貨。
 * - 追縱單位成本（costPerUnit）。
 * ============================================================
 */
public class InventoryItem {

    private Long id;
    private final Long brandId;
    private String name;
    private Quantity totalQuantity;
    private Quantity safetyStock;
    private Money costPerUnit;
    private boolean isActive;

    public InventoryItem(Long id, Long brandId, String name, Quantity totalQuantity,
                         Quantity safetyStock, Money costPerUnit, boolean isActive) {
        this.id = id;
        this.brandId = brandId;
        this.name = name;
        this.totalQuantity = totalQuantity != null ? totalQuantity : Quantity.zero("UNIT");
        this.safetyStock = safetyStock != null ? safetyStock : Quantity.zero("UNIT");
        this.costPerUnit = costPerUnit != null ? costPerUnit : Money.ZERO;
        this.isActive = isActive;
    }

    public static InventoryItem create(Long brandId, String name, String unit, Money cost) {
        return new InventoryItem(null, brandId, name, Quantity.zero(unit), Quantity.zero(unit), cost, true);
    }

    // ─── Getters ───────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getBrandId() { return brandId; }
    public String getName() { return name; }
    public Quantity getTotalQuantity() { return totalQuantity; }
    public Quantity getSafetyStock() { return safetyStock; }
    public Money getCostPerUnit() { return costPerUnit; }
    public boolean isActive() { return isActive; }

    // ─── 業務方法 ──────────────────────────────────────────────────────────

    /**
     * 更新總存量（通常由 Batch 異動連動或週期盤點調整）。
     *
     * @param newTotal 新的總存量
     */
    public void updateStock(Quantity newTotal) {
        if (!Objects.equals(this.totalQuantity.unit(), newTotal.unit())) {
            throw new IllegalArgumentException("庫存單位不匹配，無法更新");
        }
        this.totalQuantity = newTotal;
    }

    /**
     * 檢查是否低於安全水位。
     *
     * @return true 代表庫存不足，需補貨
     */
    public boolean isLowStock() {
        return totalQuantity.isLessThan(safetyStock);
    }

    public void activate() { this.isActive = true; }
    public void deactivate() { this.isActive = false; }
}
