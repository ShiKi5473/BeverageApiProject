package tw.niels.beverage_api_project.modules.report.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;

import java.util.Collection;
import java.util.List;

/**
 * 日結報表專用：批次查詢商品所屬分類名稱。
 *
 * <p>[效能] 原本日結在「每個商品」的迴圈內呼叫 findByBrand_IdAndId 並展開 categories（1 + N 次查詢），
 * 現在改成一次 JOIN 查詢取得所有商品的分類名稱。</p>
 */
public interface ProductCategoryReportQueryRepository extends Repository<ProductEntity, Long> {

    /** 商品 ID 與分類名稱的對應列 (投影介面) */
    interface ProductCategoryRow {
        Long getProductId();

        String getCategoryName();
    }

    /**
     * 依品牌 + 商品 ID 集合，查出每個商品的分類名稱。
     * 一個商品可能有多個分類，排序固定為 (商品 ID, 分類名稱)，呼叫端取每個商品第一筆，結果才是穩定的。
     */
    @Query("SELECT p.id AS productId, c.name AS categoryName " +
            "FROM ProductEntity p JOIN p.categories c " +
            "WHERE p.brand.id = :brandId AND p.id IN :productIds " +
            "ORDER BY p.id ASC, c.name ASC")
    List<ProductCategoryRow> findCategoryRows(@Param("brandId") Long brandId,
                                              @Param("productIds") Collection<Long> productIds);
}
