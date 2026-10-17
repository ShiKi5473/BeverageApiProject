package tw.niels.beverage_api_project.modules.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.report.dto.BrandSalesSummaryDto;
import tw.niels.beverage_api_project.modules.report.dto.StoreRankingDto;
import tw.niels.beverage_api_project.modules.report.entity.DailyStoreStatsEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyStoreStatsJpaRepository extends JpaRepository<DailyStoreStatsEntity, Long> {

    /**
     * 分店端：查詢指定日期區間的日結表 (依日期排序)
     */
    List<DailyStoreStatsEntity> findByStoreIdAndDateBetweenOrderByDateAsc(Long storeId, LocalDate startDate, LocalDate endDate);

    /**
     * 品牌端：查詢全品牌在指定區間的總業績 (聚合查詢)
     * 回傳 DTO: BrandSalesSummaryDto
     */
    @Query("SELECT new tw.niels.beverage_api_project.modules.report.dto.BrandSalesSummaryDto(" +
            "SUM(d.totalOrders), SUM(d.totalRevenue), SUM(d.totalDiscount), SUM(d.cancelledOrders)) " +
            "FROM DailyStoreStatsEntity d " +
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
            "FROM DailyStoreStatsEntity d " +
            "WHERE d.brandId = :brandId AND d.date BETWEEN :startDate AND :endDate " +
            "GROUP BY d.storeId " +
            "ORDER BY SUM(d.finalRevenue) DESC")
    List<StoreRankingDto> findTopStoresByRevenue(@Param("brandId") Long brandId,
                                                 @Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate);

    /**
     * 查詢指定分店的指定日期統計（單筆）
     */
    Optional<DailyStoreStatsEntity> findByStoreIdAndDate(Long storeId, LocalDate date);

    // [程式碼品質] 已移除 findTopByOrderByDateDesc (取全域最新日期)：
    // 即時統計會寫入「今天」的資料列，用它判斷最後一次完整日結一定會得到今天 (B-6 的補跑漏洞)，
    // 補漏檢查改用下方的 findTopByDateLessThanOrderByDateDesc。

    /**
     * [B-6 新增] 系統用：取得「早於指定日期」的最新一筆紀錄。
     * 用途：補漏檢查 (ReportRecoveryRunner) 要找的是「最後一次完整日結的日期」。
     * 即時統計 Handler 會寫入「今天」的資料列，若直接用全域最新日期，永遠會得到今天而誤判為「已是最新」。
     */
    Optional<DailyStoreStatsEntity> findTopByDateLessThanOrderByDateDesc(LocalDate date);

    /**
     * [B-6 新增] 即時統計原子累加 (UPSERT)。
     * <p>
     * 原本 OrderClosedReportHandler 是「讀取 → 在記憶體加總 → 存回」(read-modify-write)，
     * 兩筆訂單同時完成時會互相覆蓋而少算。這裡改成單一 SQL：
     * 不存在就新增一筆 (訂單數 1)，已存在就由資料庫在持有列鎖的情況下原子累加，
     * 衝突目標為唯一鍵 uk_daily_store_stats_store_date (store_id, date)。
     * </p>
     * 欄位名稱以 Flyway 為準：主鍵欄位 V4 已改名為 daily_store_stats_id，id 由應用程式以 TSID 提供。
     */
    @Modifying
    @Query(value = "INSERT INTO daily_store_stats (" +
            "daily_store_stats_id, store_id, brand_id, date, " +
            "total_orders, total_revenue, total_discount, final_revenue, cancelled_orders, " +
            "cash_total, line_pay_total, credit_card_total, third_party_total, unspecified_total, created_at) " +
            "VALUES (:id, :storeId, :brandId, :statDate, " +
            "1, :totalRevenue, :totalDiscount, :finalRevenue, 0, " +
            ":cashTotal, 0, :creditCardTotal, :thirdPartyTotal, :unspecifiedTotal, CURRENT_TIMESTAMP) " +
            "ON CONFLICT (store_id, date) DO UPDATE SET " +
            "total_orders = daily_store_stats.total_orders + 1, " +
            "total_revenue = daily_store_stats.total_revenue + EXCLUDED.total_revenue, " +
            "total_discount = daily_store_stats.total_discount + EXCLUDED.total_discount, " +
            "final_revenue = daily_store_stats.final_revenue + EXCLUDED.final_revenue, " +
            "cash_total = COALESCE(daily_store_stats.cash_total, 0) + EXCLUDED.cash_total, " +
            "credit_card_total = daily_store_stats.credit_card_total + EXCLUDED.credit_card_total, " +
            "third_party_total = daily_store_stats.third_party_total + EXCLUDED.third_party_total, " +
            "unspecified_total = daily_store_stats.unspecified_total + EXCLUDED.unspecified_total",
            nativeQuery = true)
    void upsertRealtimeStats(@Param("id") Long id,
                             @Param("storeId") Long storeId,
                             @Param("brandId") Long brandId,
                             @Param("statDate") LocalDate statDate,
                             @Param("totalRevenue") BigDecimal totalRevenue,
                             @Param("totalDiscount") BigDecimal totalDiscount,
                             @Param("finalRevenue") BigDecimal finalRevenue,
                             @Param("cashTotal") BigDecimal cashTotal,
                             @Param("creditCardTotal") BigDecimal creditCardTotal,
                             @Param("thirdPartyTotal") BigDecimal thirdPartyTotal,
                             @Param("unspecifiedTotal") BigDecimal unspecifiedTotal);

    /**
     * 系統用：檢查是否已存在該日報表 (避免重複執行)
     */
    boolean existsByStoreIdAndDate(Long storeId, LocalDate date);

    /**
     * 系統用：刪除該日報表 (重跑用)
     */
    void deleteByStoreIdAndDate(Long storeId, LocalDate date);
}