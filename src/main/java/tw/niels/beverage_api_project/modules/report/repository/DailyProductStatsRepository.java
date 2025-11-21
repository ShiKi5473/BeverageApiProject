package tw.niels.beverage_api_project.modules.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.report.entity.DailyProductStats;

import java.time.LocalDate;

@Repository
public interface DailyProductStatsRepository extends JpaRepository<DailyProductStats, Long> {

    /**
     * 系統用：刪除某店某日的商品統計 (重跑用)
     */
    void deleteByStoreIdAndDate(Long storeId, LocalDate date);

    // 未來可在此擴充查詢熱銷商品的方法
}