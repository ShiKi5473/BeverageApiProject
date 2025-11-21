package tw.niels.beverage_api_project.modules.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.report.dto.ProductSalesStatsDto;
import tw.niels.beverage_api_project.modules.report.entity.DailyProductStats;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailyProductStatsRepository extends JpaRepository<DailyProductStats, Long> {

    /**
     * 系統用：刪除某店某日的商品統計 (重跑用)
     */
    void deleteByStoreIdAndDate(Long storeId, LocalDate date);

    /**
     * 查詢指定分店、指定區間的商品銷售排行 (聚合查詢)
     * 依照「總銷售金額」由高到低排序
     */
    @Query("SELECT new tw.niels.beverage_api_project.modules.report.dto.ProductSalesStatsDto(" +
            "d.productId, d.productName, SUM(d.quantitySold), SUM(d.totalSalesAmount)) " +
            "FROM DailyProductStats d " +
            "WHERE d.storeId = :storeId AND d.date BETWEEN :startDate AND :endDate " +
            "GROUP BY d.productId, d.productName " +
            "ORDER BY SUM(d.totalSalesAmount) DESC")
    List<ProductSalesStatsDto> findProductSalesRanking(@Param("storeId") Long storeId,
                                                       @Param("startDate") LocalDate startDate,
                                                       @Param("endDate") LocalDate endDate);
}