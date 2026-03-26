package tw.niels.beverage_api_project.modules.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryTransactionEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryTransactionJpaRepository extends JpaRepository<InventoryTransactionEntity, Long> {

    // 找某個時間點之前的最後一筆紀錄 (用於決定期初/期末水位)
    Optional<InventoryTransactionEntity> findFirstByStore_IdAndInventoryItem_IdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
            Long storeId, Long itemId, Instant timestamp);

    // 統計區間內的進貨總量
    @Query("SELECT SUM(t.changeAmount) FROM InventoryTransactionEntity t " +
            "WHERE t.store.id = :storeId AND t.inventoryItem.id = :itemId " +
            "AND t.reasonType = 'RESTOCK' " +
            "AND t.createdAt > :start AND t.createdAt <= :end")
    BigDecimal sumRestockBetween(Long storeId, Long itemId, Instant start, Instant end);

    @Query("SELECT t FROM InventoryTransactionEntity t WHERE t.store.id = :storeId " +
            "AND t.inventoryItem.id = :itemId ORDER BY t.createdAt DESC")
    List<InventoryTransactionEntity> findTopByStoreAndItem(
            @org.springframework.data.repository.query.Param("storeId") Long storeId,
            @org.springframework.data.repository.query.Param("itemId") Long itemId,
            org.springframework.data.domain.Pageable pageable);
}