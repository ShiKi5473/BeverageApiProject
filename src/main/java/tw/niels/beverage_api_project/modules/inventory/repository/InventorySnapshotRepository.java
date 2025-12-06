package tw.niels.beverage_api_project.modules.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.inventory.entity.InventorySnapshot;

import java.util.Optional;

@Repository
public interface InventorySnapshotRepository extends JpaRepository<InventorySnapshot, Long> {

    // 查詢特定分店、特定物料的最新快照
    Optional<InventorySnapshot> findByStore_IdAndInventoryItem_Id(Long storeId, Long itemId);
}