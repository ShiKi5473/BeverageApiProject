package tw.niels.beverage_api_project.modules.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.inventory.entity.PurchaseShipmentEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseShipmentJpaRepository extends JpaRepository<PurchaseShipmentEntity, Long> {
    List<PurchaseShipmentEntity> findByStore_Brand_Id(Long brandId);

    Optional<PurchaseShipmentEntity> findByStore_Brand_IdAndId(Long brandId, Long id);

    // --- 安全防護 ---

    @Deprecated
    @Override
    @NonNull
    default Optional<PurchaseShipmentEntity> findById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止使用 findById()，請改用 findByStore_Brand_IdAndId()");
    }

    @Deprecated
    @Override
    @NonNull
    default List<PurchaseShipmentEntity> findAll() {
        throw new UnsupportedOperationException("禁止使用 findAll()，請改用 findByStore_Brand_Id()");
    }

    @Deprecated
    @Override
    default void deleteById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止使用 deleteById()，請先驗證 BrandId");
    }
}