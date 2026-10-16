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
     * [B-5 修正] 批次查詢多個品項的可用批次，並加上悲觀寫鎖 (PESSIMISTIC_WRITE)。
     * 原本 processBatchDeductions 使用的 findAvailableBatchesForItems 沒有鎖定批次，
     * 與單品項版本 (findAvailableBatchesForUpdate) 行為不一致；
     * 排序加上 b.id 作為次要條件，確保加鎖順序固定、結果可預期。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM InventoryBatchEntity b " +
            "WHERE b.store.id = :storeId AND b.inventoryItem.id IN :itemIds AND b.currentQuantity > 0 " +
            "ORDER BY b.expiryDate ASC, b.id ASC")
    List<InventoryBatchEntity> findAvailableBatchesForItemsForUpdate(@Param("storeId") Long storeId,
                                                                     @Param("itemIds") Set<Long> itemIds);

    /**
     * [B-5 新增] 分組查詢：某店「多個品項」各自的批次庫存總和 (盤點時的系統數量來源)。
     * 沒有任何批次的品項不會出現在結果中，呼叫端以 0 處理。
     */
    @Query("SELECT b.inventoryItem.id AS itemId, SUM(b.currentQuantity) AS quantity " +
            "FROM InventoryBatchEntity b " +
            "WHERE b.store.id = :storeId AND b.inventoryItem.id IN :itemIds " +
            "GROUP BY b.inventoryItem.id")
    List<ItemQuantitySum> sumQuantityByStoreAndItems(@Param("storeId") Long storeId,
                                                     @Param("itemIds") Set<Long> itemIds);

    /**
     * [B-5 新增] 分組查詢：某店「所有品項」的批次庫存總和 (盤點清單顯示用)。
     */
    @Query("SELECT b.inventoryItem.id AS itemId, SUM(b.currentQuantity) AS quantity " +
            "FROM InventoryBatchEntity b " +
            "WHERE b.store.id = :storeId " +
            "GROUP BY b.inventoryItem.id")
    List<ItemQuantitySum> sumQuantityByStore(@Param("storeId") Long storeId);

    /** 分組加總結果的投影介面 (itemId -> 批次總量) */
    interface ItemQuantitySum {
        Long getItemId();

        BigDecimal getQuantity();
    }


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
