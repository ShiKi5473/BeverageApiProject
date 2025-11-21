package tw.niels.beverage_api_project.modules.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.report.dto.BrandSalesSummaryDto;
import tw.niels.beverage_api_project.modules.report.dto.StoreRankingDto;
import tw.niels.beverage_api_project.modules.report.entity.DailyStoreStats;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailyStoreStatsRepository extends JpaRepository<DailyStoreStats, Long> {

    /**
     * 分店端：查詢指定日期區間的日結表 (依日期排序)
     */
    List<DailyStoreStats> findByStoreIdAndDateBetweenOrderByDateAsc(Long storeId, LocalDate startDate, LocalDate endDate);

    /**
     * 品牌端：查詢全品牌在指定區間的總業績 (聚合查詢)
     * 回傳 DTO: BrandSalesSummaryDto
     */
    @Query("SELECT new tw.niels.beverage_api_project.modules.report.dto.BrandSalesSummaryDto(" +
            "SUM(d.totalOrders), SUM(d.totalRevenue), SUM(d.totalDiscount), SUM(d.cancelledOrders)) " +
            "FROM DailyStoreStats d " +
            "WHERE d.brandId = :brandId AND d.date BETWEEN :startDate AND :endDate")
    BrandSalesSummaryDto aggregateBrandSales(@Param("brandId") Long brandId,
                                             @Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate);

    /**
     * 品牌端：查詢各分店在指定區間的業績排行 (Top Stores)
     * 回傳 DTO: StoreRankingDto
     */
    @Query("SELECT new tw.niels.beverage_api_project.modules.report.dto.StoreRankingDto(" +
            "d.storeId, SUM(d.finalRevenue)) " +
            "FROM DailyStoreStats d " +
            "WHERE d.brandId = :brandId AND d.date BETWEEN :startDate AND :endDate " +
            "GROUP BY d.storeId " +
            "ORDER BY SUM(d.finalRevenue) DESC")
    List<StoreRankingDto> findTopStoresByRevenue(@Param("brandId") Long brandId,
                                                 @Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate);

    /**
     * 系統用：檢查是否已存在該日報表 (避免重複執行)
     */
    boolean existsByStoreIdAndDate(Long storeId, LocalDate date);

    /**
     * 系統用：刪除該日報表 (重跑用)
     */
    void deleteByStoreIdAndDate(Long storeId, LocalDate date);
}