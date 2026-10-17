package tw.niels.beverage_api_project.modules.member.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.modules.brand.entity.BrandPointConfigEntity;
import tw.niels.beverage_api_project.modules.brand.repository.BrandPointConfigJpaRepository;
import tw.niels.beverage_api_project.modules.member.entity.MemberPointLogEntity;
import tw.niels.beverage_api_project.modules.member.repository.MemberPointLogJpaRepository;
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.user.entity.UserEntity;
import tw.niels.beverage_api_project.modules.user.entity.profile.MemberProfileEntity;
import tw.niels.beverage_api_project.modules.user.repository.MemberProfileJpaRepository;

/**
 * 舊架構的會員積點服務（直接操作 JPA Entity）。
 * <p>
 * 清理(P3)：目前專案內已沒有任何呼叫端，新流程一律透過 {@code Member} Aggregate
 * （consumePoints / earnPoints / refundPoints / adjustPoints）搭配 {@code MemberRepository.save()} 處理，
 * 點數異動與積點日誌都由 Domain Model 與 RepositoryImpl 統一負責。此類別僅為過渡期保留，後續可直接刪除。
 * 本次只把「欄位／參數名稱與型別同名」的批次改名殘留（OrderEntity OrderEntity、BrandPointConfigJpaRepository
 * BrandPointConfigJpaRepository）改回正常的小駝峰命名。
 * </p>
 */
@Service
public class MemberPointService {

    private final MemberProfileJpaRepository memberProfileRepository;
    private final MemberPointLogJpaRepository memberPointLogRepository;
    private final BrandPointConfigJpaRepository brandPointConfigRepository;

    // 預設規則 (當品牌沒有設定檔時使用)
    private static final BigDecimal DEFAULT_EARN_RATE = BigDecimal.ONE;
    private static final BigDecimal DEFAULT_REDEEM_RATE = new BigDecimal("0.1");

    public MemberPointService(MemberProfileJpaRepository memberProfileRepository,
                              MemberPointLogJpaRepository memberPointLogRepository,
                              BrandPointConfigJpaRepository brandPointConfigRepository) {
        this.memberProfileRepository = memberProfileRepository;
        this.memberPointLogRepository = memberPointLogRepository;
        this.brandPointConfigRepository = brandPointConfigRepository;
    }

    /**
     * 計算使用點數可折抵的金額
     * 
     * @param pointsToUse 要使用的點數
     * @return 可折抵的金額
     */
    public BigDecimal calculateDiscountAmount(Long pointsToUse, OrderEntity order) {
        if (pointsToUse == null || pointsToUse <= 0) return BigDecimal.ZERO;
        // 取得規則
        BigDecimal rate = DEFAULT_REDEEM_RATE;
        if (order.getBrand() != null) {
            BrandPointConfigEntity config = null;
            if (order.getBrand().getId() != null) {
                config = brandPointConfigRepository.findById(order.getBrand().getId())
                        .orElse(null);
            }

            if (config != null) {
                rate = config.getRedeemRate();
            }
        }
        return new BigDecimal(pointsToUse).multiply(rate);
    }

    /**
     * 計算訂單完成後應獲得的點數
     * 
     * @param finalAmount 訂單最終金額
     * @return 應獲得的點數
     */
    public Long calculatePointsEarned(BigDecimal finalAmount, OrderEntity order) {
        if (finalAmount == null || finalAmount.compareTo(BigDecimal.ZERO) <= 0) return 0L;

        // 取得規則
        BigDecimal rate = DEFAULT_EARN_RATE;
        if (order.getBrand() != null) {
            BrandPointConfigEntity config = null;
            if (order.getBrand().getId() != null) {
                config = brandPointConfigRepository.findById(order.getBrand().getId())
                        .orElse(null);
            }

            if (config != null) {
                rate = config.getEarnRate();
            }
        }

        if (rate.compareTo(BigDecimal.ZERO) == 0) return 0L;

        // 計算邏輯： 消費金額 / 累積門檻
        // 例如 earnRate = 30 (每30元1點)，消費 100 元 -> 100 / 30 = 3 點
        return finalAmount.divide(rate, 0, java.math.RoundingMode.FLOOR).longValue();
    }
    /**
     * 【新方法】
     * 退還點數 (在訂單取消時呼叫)
     * * @param member 會員 UserEntity 實體
     * @param order  相關訂單
     */
    @Transactional(propagation = Propagation.MANDATORY) // 確保此方法在現有交易中執行
    public void refundPoints(UserEntity member, OrderEntity order) {
        Long pointsToRefund = order.getPointsUsed();

        if (member == null || pointsToRefund == null || pointsToRefund <= 0) {
            return; // 沒有會員或沒有使用點數，直接返回
        }

        MemberProfileEntity profile = memberProfileRepository.findById(member.getUserId())
                .orElseThrow(() -> new BadRequestException("使用者 ID " + member.getUserId() + " 不是會員"));
        if (profile == null) {
            // System.err 改為 logger（P3 清理）
            org.slf4j.LoggerFactory.getLogger(MemberPointService.class)
                    .warn("試圖為非會員 UserEntity ID {} 退還點數", member.getUserId());
            return;
        }

        // 增加點數 (退還)
        long newTotalPoints = profile.getTotalPoints() + pointsToRefund;
        profile.setTotalPoints(newTotalPoints);
        memberProfileRepository.save(profile); // 儲存更新後的 Profile

        // 記錄 Log
        String reason = "訂單取消點數返還 (單號: " + order.getOrderNumber() + ")";
        MemberPointLogEntity log = new MemberPointLogEntity(member, order, pointsToRefund, newTotalPoints, reason);
        memberPointLogRepository.save(log);
    }

    /**
     * 使用點數 (在創建訂單時呼叫)
     * 
     * @param member      會員 UserEntity 實體
     * @param order       相關訂單
     * @param pointsToUse 要使用的點數
     * @throws BadRequestException 如果點數不足
     */
    @Transactional(propagation = Propagation.MANDATORY) // 確保此方法在現有交易中執行
    public void usePoints(UserEntity member, OrderEntity order, Long pointsToUse) {
        if (member == null || pointsToUse == null || pointsToUse <= 0) {
            return; // 沒有會員或沒使用點數，直接返回
        }

        MemberProfileEntity profile = memberProfileRepository.findById(member.getUserId())
                .orElseThrow(() -> new BadRequestException("使用者 ID " + member.getUserId() + " 不是會員"));

        if (profile == null) {
            throw new BadRequestException("使用者 ID " + member.getUserId() + " 不是會員");
        }

        if (profile.getTotalPoints() < pointsToUse) {
            throw new BadRequestException("會員點數不足，目前點數：" + profile.getTotalPoints());
        }

        // 扣除點數
        long newTotalPoints = profile.getTotalPoints() - pointsToUse;
        profile.setTotalPoints(newTotalPoints);
        memberProfileRepository.save(profile); // 儲存更新後的 Profile

        // 記錄 Log
        String reason = "訂單折抵 (單號: " + (order != null ? order.getOrderNumber() : "N/A") + ")";
        MemberPointLogEntity log = new MemberPointLogEntity(member, order, -pointsToUse, newTotalPoints, reason);
        memberPointLogRepository.save(log);
    }

    /**
     * 賺取點數 (在訂單完成時呼叫)
     * 
     * @param member       會員 UserEntity 實體
     * @param order        相關訂單
     * @param pointsEarned 賺取的點數
     */
    @Transactional(propagation = Propagation.MANDATORY) // 確保此方法在現有交易中執行
    public void earnPoints(UserEntity member, OrderEntity order, Long pointsEarned) {
        if (member == null || pointsEarned == null || pointsEarned <= 0) {
            return; // 沒有會員或沒賺取點數，直接返回
        }

        MemberProfileEntity profile = memberProfileRepository.findById(member.getUserId())
                .orElseThrow(() -> new BadRequestException("使用者 ID " + member.getUserId() + " 不是會員"));
        if (profile == null) {
            // 理論上此時 profile 不應為 null，但還是加上防禦性檢查
            // System.err 改為 logger（P3 清理）
            org.slf4j.LoggerFactory.getLogger(MemberPointService.class)
                    .warn("試圖為非會員 UserEntity ID {} 增加點數", member.getUserId());
            return;
        }

        // 增加點數
        long newTotalPoints = profile.getTotalPoints() + pointsEarned;
        profile.setTotalPoints(newTotalPoints);
        memberProfileRepository.save(profile); // 儲存更新後的 Profile

        // 記錄 Log
        String reason = "訂單消費獲得 (單號: " + (order != null ? order.getOrderNumber() : "N/A") + ")";
        MemberPointLogEntity log = new MemberPointLogEntity(member, order, pointsEarned, newTotalPoints, reason);
        memberPointLogRepository.save(log);
    }

    /**
     * 調整點數 (通用方法，供管理員手動調整或系統其他邏輯使用)
     */
    @Transactional
    public void adjustPoints(Long brandId, Long memberId, Long points, String reason, String reasonType) {
        UserEntity member = memberProfileRepository.findById(memberId)
                .map(MemberProfileEntity::getUser)
                .orElseThrow(() -> new BadRequestException("找不到會員，ID：" + memberId));

        if (!member.getBrand().getId().equals(brandId)) {
            throw new BadRequestException("會員不屬於該品牌");
        }

        MemberProfileEntity profile = member.getMemberProfile();
        long newTotalPoints = profile.getTotalPoints() + points;
        if (newTotalPoints < 0) {
            throw new BadRequestException("調整後點數不能小於 0");
        }

        profile.setTotalPoints(newTotalPoints);
        memberProfileRepository.save(profile);

        MemberPointLogEntity log = new MemberPointLogEntity(member, null, points, newTotalPoints, reason);
        memberPointLogRepository.save(log);
    }
}
