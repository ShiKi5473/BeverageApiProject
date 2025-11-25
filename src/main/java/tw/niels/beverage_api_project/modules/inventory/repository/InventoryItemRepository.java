package tw.niels.beverage_api_project.modules.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryItem;

import java.util.List;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
    List<InventoryItem> findByBrand_Id(Long brandId);
}