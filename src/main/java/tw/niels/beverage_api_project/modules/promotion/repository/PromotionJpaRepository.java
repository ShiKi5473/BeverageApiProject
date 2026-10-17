package tw.niels.beverage_api_project.modules.promotion.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.promotion.entity.PromotionEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PromotionJpaRepository extends JpaRepository<PromotionEntity, Long> {

    /**
     * 查詢該品牌「已啟用且尚未結束」的活動（含尚未開始者），並一併載入適用商品。
     * <p>
     * 修改：取代原本的 findActivePromotionEntitysByBrand（批次改名殘留的方法名，且只查「當下」有效的活動）。
     * 1. 結果會被轉成 PromotionRule 存入快取，若只快取「當下有效」的活動，
     *    快取期間才開始的活動要等快取過期才會生效；改為快取所有未結束的活動，計算時再依當下時間過濾。
     * 2. JOIN FETCH applicableProducts：轉成 PromotionRule 時需要適用商品 ID，避免逐筆延遲載入（N+1），
     *    也讓轉換可以在 Repository 交易結束後進行。
     * </p>
     */
    @Query("SELECT DISTINCT p FROM PromotionEntity p " +
            "LEFT JOIN FETCH p.applicableProducts " +
            "WHERE p.brand.id = :brandId " +
            "AND p.isActive = true " +
            "AND p.endDate >= :now")
    List<PromotionEntity> findNotEndedByBrandWithProducts(@Param("brandId") Long brandId,
                                                          @Param("now") LocalDateTime now);

    // 管理後台列表用
    List<PromotionEntity> findByBrand_Id(Long brandId);

    /**
     * 新增：管理後台列表用（一併載入適用商品，轉成 PromotionResponseDto 時不會逐筆延遲載入）。
     */
    @EntityGraph(attributePaths = "applicableProducts")
    List<PromotionEntity> findWithProductsByBrand_Id(Long brandId);

    Optional<PromotionEntity> findByBrand_IdAndId(Long brandId, Long id);

    // --- 安全防護 ---
    @Deprecated
    @Override
    @NonNull
    default List<PromotionEntity> findAll() {
        throw new UnsupportedOperationException("禁止使用 findAll()，請改用 findByBrand_Id()");
    }

    @Deprecated
    @Override
    @NonNull
    default Optional<PromotionEntity> findById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止使用 findById()，請改用 findByBrand_IdAndId()");
    }
}
