package tw.niels.beverage_api_project.modules.product.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.product.entity.ProductOption;

@Repository
public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {
    Set<ProductOption> findByOptionGroup_Brand_IdAndIdIn(Long brandId, List<Long> optionIds);

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
