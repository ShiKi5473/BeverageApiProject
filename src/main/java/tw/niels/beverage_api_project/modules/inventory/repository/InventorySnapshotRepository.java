package tw.niels.beverage_api_project.modules.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.inventory.entity.InventorySnapshot;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventorySnapshotRepository extends JpaRepository<InventorySnapshot, Long> {
    //查詢該分店目前的庫存快照
    List<InventorySnapshot> findByStore_Id(Long storeId);

    // 查詢特定分店、特定物料的最新快照
    Optional<InventorySnapshot> findByStore_IdAndInventoryItem_Id(Long storeId, Long itemId);

    // 一次查詢該店、多個物料對應的快照
    List<InventorySnapshot> findByStore_IdAndInventoryItem_IdIn(Long storeId, Collection<Long> itemIds);
}