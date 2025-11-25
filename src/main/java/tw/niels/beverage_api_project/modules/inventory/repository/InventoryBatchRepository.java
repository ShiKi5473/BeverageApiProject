package tw.niels.beverage_api_project.modules.inventory.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryBatch;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {


    Optional<InventoryBatch> findByShipment_Store_Brand_IdAndId(Long brandId, Long id);

    /**
     * FIFO 核心查詢：
     * 1. 找出特定店家 (透過 shipment 關聯)
     * 2. 特定商品 (inventoryItem)
     * 3. 還有剩餘數量 (currentQuantity > 0)
     * 4. 依照效期由舊到新排序 (ORDER BY expiryDate ASC)
     * 5. 加悲觀鎖 (PESSIMISTIC_WRITE) 防止併發超賣
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM InventoryBatch b " +
            "WHERE b.shipment.store.id = :storeId " +
            "AND b.inventoryItem.id = :itemId " +
            "AND b.currentQuantity > 0 " +
            "ORDER BY b.expiryDate ASC")
    List<InventoryBatch> findAvailableBatchesForUpdate(@Param("storeId") Long storeId,
                                                       @Param("itemId") Long itemId);

    /**
     * 查詢某店某商品的總庫存量 (不加鎖，僅供查詢)
     */
    @Query("SELECT SUM(b.currentQuantity) FROM InventoryBatch b " +
            "WHERE b.shipment.store.id = :storeId " +
            "AND b.inventoryItem.id = :itemId")
    Optional<BigDecimal> sumQuantityByStoreAndItem(@Param("storeId") Long storeId,
                                                   @Param("itemId") Long itemId);
    @Deprecated
    @Override
    @NonNull
    default Optional<InventoryBatch> findById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止使用 findById()，請改用 findByShipment_Store_Brand_IdAndId()");
    }

    @Deprecated
    @Override
    @NonNull
    default List<InventoryBatch> findAll() {
        throw new UnsupportedOperationException("禁止使用 findAll()");
    }

    @Deprecated
    @Override
    default void deleteById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止使用 deleteById()，請先驗證 BrandId");
    }
}
