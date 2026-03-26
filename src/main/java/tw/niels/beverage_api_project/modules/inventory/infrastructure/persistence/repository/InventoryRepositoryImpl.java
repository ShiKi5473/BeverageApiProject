package tw.niels.beverage_api_project.modules.inventory.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.inventory.domain.model.InventoryBatch;
import tw.niels.beverage_api_project.modules.inventory.domain.model.InventoryItem;
import tw.niels.beverage_api_project.modules.inventory.domain.model.InventoryTransaction;
import tw.niels.beverage_api_project.modules.inventory.domain.repository.InventoryRepository;
import tw.niels.beverage_api_project.modules.inventory.infrastructure.mapper.InventoryMapper;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryBatchJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryItemJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryTransactionJpaRepository;

/**
 * ============================================================
 * Bounded Context: Inventory
 * Layer: Infrastructure - Repository Implementation
 * ============================================================
 *
 * Inventory BC Domain Repository 的 Infrastructure 層實現
 *
 * 注意：InventoryBatch 和 InventoryTransaction 的 Mapper 需要 unit 參數，
 * 由此類在查詢時先取得對應 InventoryItem 的 unit 再傳入。
 * ============================================================
 */
@Repository
public class InventoryRepositoryImpl implements InventoryRepository {

    private final InventoryItemJpaRepository itemJpaRepo;
    private final InventoryBatchJpaRepository batchJpaRepo;
    private final InventoryTransactionJpaRepository transactionJpaRepo;

    public InventoryRepositoryImpl(InventoryItemJpaRepository itemJpaRepo,
                                    InventoryBatchJpaRepository batchJpaRepo,
                                    InventoryTransactionJpaRepository transactionJpaRepo) {
        this.itemJpaRepo = itemJpaRepo;
        this.batchJpaRepo = batchJpaRepo;
        this.transactionJpaRepo = transactionJpaRepo;
    }

    // ─── InventoryItem 操作 ──────────────────────────────────────────────

    @Override
    public Optional<InventoryItem> findItemById(Long brandId, Long itemId) {
        return itemJpaRepo.findByBrandIdAndIdForUpdate(brandId, itemId)
            // 注意：這裡使用悲觀鎖查詢，若不需要鎖定可改用無鎖版本
            // 若不需要鎖定，可使用：itemJpaRepo.findByBrand_IdAndId(brandId, itemId)
            // 視呼叫情境而定，建議加入非鎖定版本的 findItemByIdReadOnly
            .map(InventoryMapper::toItemDomain);
    }

    @Override
    public List<InventoryItem> findActiveItemsByBrand(Long brandId) {
        return itemJpaRepo.findByBrand_Id(brandId)
            .stream()
            .filter((tw.niels.beverage_api_project.modules.inventory.entity.InventoryItemEntity i) -> Boolean.TRUE.equals(i.getIsActive()))
            .map(InventoryMapper::toItemDomain)
            .collect(Collectors.toList());
    }

    @Override
    public void saveItem(InventoryItem item) {
        tw.niels.beverage_api_project.modules.inventory.entity.InventoryItemEntity jpaEntity = InventoryMapper.toItemEntity(item);
        tw.niels.beverage_api_project.modules.inventory.entity.InventoryItemEntity saved = itemJpaRepo.save(jpaEntity);
        if (item.getId() == null && saved.getId() != null) {
            try {
                var setIdMethod = InventoryItem.class.getDeclaredMethod("setId", Long.class);
                setIdMethod.setAccessible(true);
                setIdMethod.invoke(item, saved.getId());
            } catch (Exception e) {
                System.err.println("警告：無法同步庫存品項 ID 回 Domain Model：" + e.getMessage());
            }
        }
    }

    // ─── InventoryBatch 操作 ─────────────────────────────────────────────

    @Override
    public List<InventoryBatch> findBatchesByStoreAndItem(Long storeId, Long itemId) {
        // 取得 unit（從 InventoryItem 取，若取不到使用預設 "UNIT"）
        String unit = itemJpaRepo.findByIdForSystem(itemId)
            .map((tw.niels.beverage_api_project.modules.inventory.entity.InventoryItemEntity i) -> i.getUnit() != null ? i.getUnit() : "UNIT")
            .orElse("UNIT");

        return batchJpaRepo.findByStore_IdAndInventoryItem_Id(storeId, itemId)
            .stream()
            .map(jpa -> InventoryMapper.toBatchDomain(jpa, unit))
            .collect(Collectors.toList());
    }

    @Override
    public void saveBatch(InventoryBatch batch) {
        var jpaEntity = InventoryMapper.toBatchEntity(batch);
        var saved = batchJpaRepo.save(jpaEntity);
        if (batch.getId() == null && saved.getId() != null) {
            try {
                var setIdMethod = InventoryBatch.class.getDeclaredMethod("setId", Long.class);
                setIdMethod.setAccessible(true);
                setIdMethod.invoke(batch, saved.getId());
            } catch (Exception e) {
                System.err.println("警告：無法同步批次 ID 回 Domain Model：" + e.getMessage());
            }
        }
    }

    // ─── InventoryTransaction 操作 ────────────────────────────────────────

    @Override
    public void saveTransaction(InventoryTransaction transaction) {
        var jpaEntity = InventoryMapper.toTransactionEntity(transaction);
        transactionJpaRepo.save(jpaEntity);
        // Transaction 是不可變稽核日誌，只做 INSERT，不需要回填 ID
    }

    @Override
    public List<InventoryTransaction> findRecentTransactions(Long storeId, Long itemId, int limit) {
        // 取得 unit（供 Mapper 使用）
        String unit = itemJpaRepo.findByIdForSystem(itemId)
            .map((tw.niels.beverage_api_project.modules.inventory.entity.InventoryItemEntity i) -> i.getUnit() != null ? i.getUnit() : "UNIT")
            .orElse("UNIT");

        // 使用 Pageable 限制筆數
        return transactionJpaRepo.findTopByStoreAndItem(
                storeId, itemId, PageRequest.of(0, limit))
            .stream()
            .map(jpa -> InventoryMapper.toTransactionDomain(jpa, unit))
            .collect(Collectors.toList());
    }
}
