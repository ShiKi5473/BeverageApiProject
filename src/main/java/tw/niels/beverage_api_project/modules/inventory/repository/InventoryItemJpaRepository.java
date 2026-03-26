package tw.niels.beverage_api_project.modules.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryItemEntity;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 庫存品項 JPA 倉儲介面（重命名自 InventoryItemRepository）
 * Layer: Infrastructure - Spring Data JPA Repository
 */
@Repository
public interface InventoryItemJpaRepository extends JpaRepository<InventoryItemEntity, Long> {

    List<InventoryItemEntity> findByBrand_Id(Long brandId);

    Optional<InventoryItemEntity> findByBrand_IdAndName(Long brandId, String name);

    Optional<InventoryItemEntity> findByBrand_IdAndId(Long brandId, Long id);

    List<InventoryItemEntity> findByBrand_IdAndIdIn(Long brandId, Collection<Long> ids);

    @Modifying
    @Query("UPDATE InventoryItemEntity i SET i.totalQuantity = i.totalQuantity + :amount WHERE i.id = :id")
    void increaseTotalQuantity(Long id, BigDecimal amount);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryItemEntity i WHERE i.brand.id = :brandId AND i.id = :id")
    Optional<InventoryItemEntity> findByBrandIdAndIdForUpdate(Long brandId, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryItemEntity i WHERE i.brand.id = :brandId AND i.id IN :ids")
    List<InventoryItemEntity> findByBrandIdAndIdInForUpdate(Long brandId, Set<Long> ids);

    // 系統專用查詢（供 InventoryRepositoryImpl 內部使用，取得 unit）
    @Query("SELECT i FROM InventoryItemEntity i WHERE i.id = :itemId")
    Optional<InventoryItemEntity> findByIdForSystem(Long itemId);

    // 保留所有原有方法
}
