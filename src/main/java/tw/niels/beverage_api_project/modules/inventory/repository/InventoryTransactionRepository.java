package tw.niels.beverage_api_project.modules.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    // 查詢某店、某物料、在指定時間點「之前」的最後一筆異動 (用於找期初餘額)
    Optional<InventoryTransaction> findFirstByStore_StoreIdAndInventoryItem_InventoryItemIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
            Long storeId, Long itemId, Instant timestamp);

    // 查詢期間內的進貨總量
    @Query("SELECT SUM(t.changeAmount) FROM InventoryTransaction t " +
            "WHERE t.store.storeId = :storeId AND t.inventoryItem.inventoryItemId = :itemId " +
            "AND t.reasonType = 'RESTOCK' " +
            "AND t.createdAt > :start AND t.createdAt <= :end")
    BigDecimal sumRestockBetween(Long storeId, Long itemId, Instant start, Instant end);
}