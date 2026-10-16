package tw.niels.beverage_api_project.modules.order.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;

import java.util.Collection;
import java.util.List;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Infrastructure - Spring Data JPA Repository（唯讀查詢）
 * ============================================================
 *
 * 新增：訂單建立時批次查詢商品用的唯讀倉儲。
 *
 * 為什麼不直接用 ProductJpaRepository.findByBrand_IdAndIdIn？
 * 建立訂單品項需要商品的分類（快照的分類名稱）與選項群組（驗證選項是否屬於該商品），
 * 兩者皆為 LAZY 集合，逐筆存取會產生 N+1 查詢；此查詢以 FETCH JOIN 一次載入。
 * 放在 Order 模組內，避免修改 Product 模組（由其他串流維護）的 Repository。
 * ============================================================
 */
public interface OrderProductLookupJpaRepository extends Repository<ProductEntity, Long> {

    /**
     * 依品牌批次查詢商品，並一併載入分類與選項群組。
     *
     * @param brandId    品牌 ID（多租戶隔離）
     * @param productIds 商品 ID 集合
     * @return 屬於該品牌的商品清單
     */
    @Query("""
        SELECT DISTINCT p
        FROM ProductEntity p
        LEFT JOIN FETCH p.categories
        LEFT JOIN FETCH p.optionGroups
        WHERE p.brand.id = :brandId AND p.id IN :productIds
    """)
    List<ProductEntity> findForOrderByBrandIdAndIdIn(@Param("brandId") Long brandId,
                                                    @Param("productIds") Collection<Long> productIds);
}
