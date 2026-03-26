package tw.niels.beverage_api_project.modules.inventory.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryBatchEntity;

@Repository
public interface InventoryBatchJpaRepository extends JpaRepository<InventoryBatchEntity, Long> {


    Optional<InventoryBatchEntity> findByStore_Brand_IdAndId(Long brandId, Long id);

    List<InventoryBatchEntity> findByStore_Id(Long storeId);

    List<InventoryBatchEntity> findByStore_IdAndInventoryItem_Id(Long storeId, Long inventoryItemId);

    /**
     * FIFO 核心查詢：
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM InventoryBatchEntity b " +
            "WHERE b.store.id = :storeId " +  // <-- 關鍵修正
            "AND b.inventoryItem.id = :itemId " +
            "AND b.currentQuantity > 0 " +
            "ORDER BY b.expiryDate ASC")
    List<InventoryBatchEntity> findAvailableBatchesForUpdate(@Param("storeId") Long storeId,
                                                       @Param("itemId") Long itemId);

    // 批次查詢多個 Item 的可用批次 (用於 performAudit)
    @Query("SELECT b FROM InventoryBatchEntity b WHERE b.store.id = :storeId AND b.inventoryItem.id IN :itemIds AND b.currentQuantity > 0 ORDER BY b.expiryDate ASC")
    List<InventoryBatchEntity> findAvailableBatchesForItems(@Param("storeId") Long storeId, @Param("itemIds") Set<Long> itemIds);


    /**
     * 查詢某店某商品的總庫存量
     */
    @Query("SELECT SUM(b.currentQuantity) FROM InventoryBatchEntity b " +
            "WHERE b.store.id = :storeId " + // <-- 關鍵修正
            "AND b.inventoryItem.id = :itemId")
    Optional<BigDecimal> sumQuantityByStoreAndItem(@Param("storeId") Long storeId,
                                                   @Param("itemId") Long itemId);

    // 此方法已經是正確的 (V9 已修正)
    Optional<InventoryBatchEntity> findTopByStore_IdAndInventoryItem_IdOrderByExpiryDateDesc(Long storeId, Long itemId);
    
    @Deprecated
    @Override
    @NonNull
    default Optional<InventoryBatchEntity> findById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止使用 findById()，請改用 findByShipment_Store_Brand_IdAndId()");
    }

    @Deprecated
    @Override
    @NonNull
    default List<InventoryBatchEntity> findAll() {
        throw new UnsupportedOperationException("禁止使用 findAll()");
    }

    @Deprecated
    @Override
    default void deleteById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止使用 deleteById()，請先驗證 BrandId");
    }
}
