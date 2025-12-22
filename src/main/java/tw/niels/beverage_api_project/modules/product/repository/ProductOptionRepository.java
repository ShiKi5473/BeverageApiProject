package tw.niels.beverage_api_project.modules.product.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.product.entity.ProductOption;

@Repository
public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

    /**
     * 【優化版】批次查詢選項，並同時抓取 OptionGroup
     * 使用 JOIN FETCH 確保不會因為存取關聯而觸發額外 SQL
     */
    @Query("""
        SELECT po
        FROM ProductOption po 
        JOIN FETCH po.optionGroup og 
        WHERE og.brand.id = :brandId 
        AND po.id IN :optionIds
    """)
    Set<ProductOption> findByOptionGroup_Brand_IdAndIdIn(
            @Param("brandId") Long brandId,
            @Param("optionIds") Collection<Long> optionIds // 改用 Collection 更通用
    );

    @Deprecated
    Set<ProductOption> findByIdIn(List<Long> optionIds);

    // 修正：使用 Brand_Id
    Optional<ProductOption> findByOptionGroup_Brand_IdAndId(Long brandId, Long id);

    // --- 安全防護 ---

    @Deprecated
    @Override
    @NonNull
    default Optional<ProductOption> findById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止使用 findById()，請改用 findByOptionGroup_Brand_IdAndId()");
    }

    @Deprecated
    @Override
    @NonNull
    default List<ProductOption> findAll() {
        throw new UnsupportedOperationException("禁止使用 findAll()");
    }

    @Deprecated
    @Override
    default void deleteById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止使用 deleteById()，請先驗證 BrandId");
    }
}
