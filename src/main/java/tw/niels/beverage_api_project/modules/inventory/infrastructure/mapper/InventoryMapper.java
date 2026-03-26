package tw.niels.beverage_api_project.modules.inventory.infrastructure.mapper;

import tw.niels.beverage_api_project.common.domain.vo.Money;
import tw.niels.beverage_api_project.modules.inventory.domain.model.*;

import java.math.BigDecimal;

/**
 * ============================================================
 * Bounded Context: Inventory
 * Layer: Infrastructure - Mapper
 * ============================================================
 *
 * Inventory BC - JPA Entity ↔ Domain Model 雙向映射工具
 *
 * 映射規則：
 * - InventoryItem：unit 欄位在 JPA entity 中有對應，直接使用
 * - safetyStock：JPA Integer ↔ Domain Quantity.value（BigDecimal）
 * - InventoryBatch/InventoryTransaction：JPA 無 unit 欄位，
 *   需從呼叫方傳入 unit（通常從 InventoryItem 取得）
 * ============================================================
 */
public class InventoryMapper {

    // ─── InventoryItem 映射 ──────────────────────────────────────────────

    public static InventoryItem toItemDomain(
            tw.niels.beverage_api_project.modules.inventory.entity.InventoryItemEntity jpa) {
        if (jpa == null) return null;

        // unit 在 InventoryItem JPA entity 中有欄位，直接取
        String unit = jpa.getUnit() != null ? jpa.getUnit() : "UNIT";

        // totalQuantity：BigDecimal + unit → Quantity VO
        Quantity totalQty = Quantity.of(
            jpa.getTotalQuantity() != null ? jpa.getTotalQuantity() : BigDecimal.ZERO,
            unit
        );

        // safetyStock：Integer → BigDecimal → Quantity VO（unit 與 totalQuantity 相同）
        Quantity safetyStk = Quantity.of(
            jpa.getSafetyStock() != null
                ? new BigDecimal(jpa.getSafetyStock())
                : BigDecimal.ZERO,
            unit
        );

        return new InventoryItem(
            jpa.getId(),
            jpa.getBrand() != null ? jpa.getBrand().getId() : null,
            jpa.getName(),
            totalQty,
            safetyStk,
            jpa.getCostPerUnit() != null ? Money.of(jpa.getCostPerUnit()) : Money.ZERO,
            Boolean.TRUE.equals(jpa.getIsActive())
        );
    }

    public static tw.niels.beverage_api_project.modules.inventory.entity.InventoryItemEntity toItemEntity(
            InventoryItem domain) {
        if (domain == null) return null;

        var jpa = new tw.niels.beverage_api_project.modules.inventory.entity.InventoryItemEntity();
        if (domain.getId() != null) jpa.setId(domain.getId());

        // Brand Proxy
        if (domain.getBrandId() != null) {
            var brand = new tw.niels.beverage_api_project.modules.brand.entity.BrandEntity();
            brand.setId(domain.getBrandId());
            jpa.setBrand(brand);
        }

        jpa.setName(domain.getName());
        // unit 從 Quantity VO 取出
        jpa.setUnit(domain.getTotalQuantity() != null ? domain.getTotalQuantity().unit() : "UNIT");
        jpa.setTotalQuantity(domain.getTotalQuantity() != null ? domain.getTotalQuantity().value() : BigDecimal.ZERO);
        // safetyStock：Quantity.value().intValue() → Integer
        jpa.setSafetyStock(domain.getSafetyStock() != null
            ? domain.getSafetyStock().value().intValue()
            : 0);
        jpa.setCostPerUnit(domain.getCostPerUnit() != null ? domain.getCostPerUnit().amount() : null);
        jpa.setIsActive(domain.isActive());
        return jpa;
    }

    // ─── InventoryBatch 映射 ─────────────────────────────────────────────

    /**
     * JPA InventoryBatch → Domain InventoryBatch
     *
     * @param jpa  JPA InventoryBatch Entity
     * @param unit 數量單位（從 InventoryItem 取得，JPA Batch 無 unit 欄位）
     */
    public static InventoryBatch toBatchDomain(
            tw.niels.beverage_api_project.modules.inventory.entity.InventoryBatchEntity jpa,
            String unit) {
        if (jpa == null) return null;

        String safeUnit = unit != null ? unit : "UNIT";

        return new InventoryBatch(
            jpa.getId(),
            jpa.getInventoryItem() != null ? jpa.getInventoryItem().getId() : null,
            jpa.getStore() != null ? jpa.getStore().getId() : null,
            Quantity.of(
                jpa.getQuantityReceived() != null ? jpa.getQuantityReceived() : BigDecimal.ZERO,
                safeUnit
            ),
            Quantity.of(
                jpa.getCurrentQuantity() != null ? jpa.getCurrentQuantity() : BigDecimal.ZERO,
                safeUnit
            ),
            jpa.getProductionDate(),
            jpa.getExpiryDate()
        );
    }

    public static tw.niels.beverage_api_project.modules.inventory.entity.InventoryBatchEntity toBatchEntity(
            InventoryBatch domain) {
        if (domain == null) return null;

        var jpa = new tw.niels.beverage_api_project.modules.inventory.entity.InventoryBatchEntity();
        if (domain.getId() != null) jpa.setId(domain.getId());

        // InventoryItem Proxy
        if (domain.getInventoryItemId() != null) {
            var item = new tw.niels.beverage_api_project.modules.inventory.entity.InventoryItemEntity();
            item.setId(domain.getInventoryItemId());
            jpa.setInventoryItem(item);
        }

        // Store Proxy
        if (domain.getStoreId() != null) {
            var store = new tw.niels.beverage_api_project.modules.store.entity.StoreEntity();
            store.setId(domain.getStoreId());
            jpa.setStore(store);
        }

        jpa.setQuantityReceived(domain.getQuantityReceived() != null
            ? domain.getQuantityReceived().value() : BigDecimal.ZERO);
        jpa.setCurrentQuantity(domain.getCurrentQuantity() != null
            ? domain.getCurrentQuantity().value() : BigDecimal.ZERO);

        jpa.setProductionDate(domain.getProductionDate());
        jpa.setExpiryDate(domain.getExpiryDate());

        return jpa;
    }

    // ─── InventoryTransaction 映射 ────────────────────────────────────────

    /**
     * JPA InventoryTransaction → Domain InventoryTransaction
     *
     * @param jpa  JPA InventoryTransaction Entity
     * @param unit 數量單位（從 InventoryItem 取得，JPA Transaction 無 unit 欄位）
     */
    public static InventoryTransaction toTransactionDomain(
            tw.niels.beverage_api_project.modules.inventory.entity.InventoryTransactionEntity jpa,
            String unit) {
        if (jpa == null) return null;

        String safeUnit = unit != null ? unit : "UNIT";

        return new InventoryTransaction(
            jpa.getId(),
            jpa.getInventoryItem() != null ? jpa.getInventoryItem().getId() : null,
            jpa.getStore() != null ? jpa.getStore().getId() : null,
            Quantity.of(
                jpa.getChangeAmount() != null ? jpa.getChangeAmount() : BigDecimal.ZERO,
                safeUnit
            ),
            Quantity.of(
                jpa.getBalanceAfter() != null ? jpa.getBalanceAfter() : BigDecimal.ZERO,
                safeUnit
            ),
            jpa.getReasonType(),
            jpa.getOperator() != null ? jpa.getOperator().getId() : null,
            jpa.getNote()
        );
    }

    public static tw.niels.beverage_api_project.modules.inventory.entity.InventoryTransactionEntity toTransactionEntity(
            InventoryTransaction domain) {
        if (domain == null) return null;

        var jpa = new tw.niels.beverage_api_project.modules.inventory.entity.InventoryTransactionEntity();

        // InventoryItem Proxy
        if (domain.getInventoryItemId() != null) {
            var item = new tw.niels.beverage_api_project.modules.inventory.entity.InventoryItemEntity();
            item.setId(domain.getInventoryItemId());
            jpa.setInventoryItem(item);
        }

        // Store Proxy
        if (domain.getStoreId() != null) {
            var store = new tw.niels.beverage_api_project.modules.store.entity.StoreEntity();
            store.setId(domain.getStoreId());
            jpa.setStore(store);
        }

        jpa.setChangeAmount(domain.getChangeAmount() != null
            ? domain.getChangeAmount().value() : BigDecimal.ZERO);
        jpa.setBalanceAfter(domain.getBalanceAfter() != null
            ? domain.getBalanceAfter().value() : BigDecimal.ZERO);
        jpa.setReasonType(domain.getReasonType());
        jpa.setNote(domain.getNote());

        // Operator Proxy
        if (domain.getOperatorId() != null) {
            var operator = new tw.niels.beverage_api_project.modules.user.entity.UserEntity();
            operator.setId(domain.getOperatorId());
            jpa.setOperator(operator);
        }

        return jpa;
    }
}
