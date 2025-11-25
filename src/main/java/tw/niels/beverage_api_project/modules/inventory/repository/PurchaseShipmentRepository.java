package tw.niels.beverage_api_project.modules.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.inventory.entity.PurchaseShipment;

@Repository
public interface PurchaseShipmentRepository extends JpaRepository<PurchaseShipment, Long> {
}