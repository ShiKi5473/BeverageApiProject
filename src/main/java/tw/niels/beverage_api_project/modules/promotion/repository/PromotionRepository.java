package tw.niels.beverage_api_project.modules.promotion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.promotion.entity.Promotion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    // 查詢該品牌當下有效的所有活動
    @Query("SELECT p FROM Promotion p WHERE p.brand.id = :brandId " +
            "AND p.isActive = true " +
            "AND p.startDate <= :now AND p.endDate >= :now")
    List<Promotion> findActivePromotionsByBrand(@Param("brandId") Long brandId,
                                                @Param("now") LocalDateTime now);

    // 管理後台列表用
    List<Promotion> findByBrand_Id(Long brandId);

    Optional<Promotion> findByBrand_IdAndId(Long brandId, Long id);

    // --- 安全防護 ---
    @Deprecated
    @Override
    @NonNull
    default List<Promotion> findAll() {
        throw new UnsupportedOperationException("禁止使用 findAll()，請改用 findByBrand_Id()");
    }

    @Deprecated
    @Override
    @NonNull
    default Optional<Promotion> findById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止使用 findById()，請改用 findByBrand_IdAndId()");
    }
}