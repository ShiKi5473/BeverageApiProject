package tw.niels.beverage_api_project.modules.member.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.modules.member.entity.MemberPointLog;
import tw.niels.beverage_api_project.modules.member.repository.MemberPointLogRepository;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.entity.profile.MemberProfile;
import tw.niels.beverage_api_project.modules.user.repository.MemberProfileRepository;

@Service
public class MemberPointService {

    private final MemberProfileRepository memberProfileRepository;
    private final MemberPointLogRepository memberPointLogRepository;

    // 預設規則 (當品牌沒有設定檔時使用)
    private static final BigDecimal DEFAULT_EARN_RATE = BigDecimal.ONE;
    private static final BigDecimal DEFAULT_REDEEM_RATE = new BigDecimal("0.1");

    public MemberPointService(MemberProfileRepository memberProfileRepository,
            MemberPointLogRepository memberPointLogRepository) {
        this.memberProfileRepository = memberProfileRepository;
        this.memberPointLogRepository = memberPointLogRepository;
    }

    /**
     * 計算使用點數可折抵的金額
     * 
     * @param pointsToUse 要使用的點數
     * @return 可折抵的金額
     */
    public BigDecimal calculateDiscountAmount(Long pointsToUse, Order order) {
        if (pointsToUse == null || pointsToUse <= 0) return BigDecimal.ZERO;
        // 取得規則
        BigDecimal rate = DEFAULT_REDEEM_RATE;
        if (order.getBrand().getPointConfig() != null) {
            rate = order.getBrand().getPointConfig().getRedeemRate();
        }

        return new BigDecimal(pointsToUse).multiply(rate);
    }

    /**
     * 計算訂單完成後應獲得的點數
     * 
     * @param finalAmount 訂單最終金額
     * @return 應獲得的點數
     */
    public Long calculatePointsEarned(BigDecimal finalAmount, Order order) {
        if (finalAmount == null || finalAmount.compareTo(BigDecimal.ZERO) <= 0) return 0L;

        // 取得規則
        BigDecimal rate = DEFAULT_EARN_RATE;
        if (order.getBrand().getPointConfig() != null) {
            rate = order.getBrand().getPointConfig().getEarnRate();
        }

        if (rate.compareTo(BigDecimal.ZERO) == 0) return 0L;

        // 計算邏輯： 消費金額 / 累積門檻
        // 例如 earnRate = 30 (每30元1點)，消費 100 元 -> 100 / 30 = 3 點
        return finalAmount.divide(rate, 0, java.math.RoundingMode.FLOOR).longValue();
    }
    /**
     * 【新方法】
     * 退還點數 (在訂單取消時呼叫)
     * * @param member 會員 User 實體
     * @param order  相關訂單
     */
    @Transactional(propagation = Propagation.MANDATORY) // 確保此方法在現有交易中執行
    public void refundPoints(User member, Order order) {
        Long pointsToRefund = order.getPointsUsed();

        if (member == null || pointsToRefund == null || pointsToRefund <= 0) {
            return; // 沒有會員或沒有使用點數，直接返回
        }

        MemberProfile profile = memberProfileRepository.findById(member.getUserId())
                .orElseThrow(() -> new BadRequestException("使用者 ID " + member.getUserId() + " 不是會員"));
        if (profile == null) {
            System.err.println("警告：試圖為非會員 User ID " + member.getUserId() + " 退還點數");
            return;
        }

        // 增加點數 (退還)
        long newTotalPoints = profile.getTotalPoints() + pointsToRefund;
        profile.setTotalPoints(newTotalPoints);
        memberProfileRepository.save(profile); // 儲存更新後的 Profile

        // 記錄 Log
        String reason = "訂單取消點數返還 (單號: " + order.getOrderNumber() + ")";        MemberPointLog log = new MemberPointLog(member, order, pointsToRefund, newTotalPoints, reason);
        memberPointLogRepository.save(log);
    }

    /**
     * 使用點數 (在創建訂單時呼叫)
     * 
     * @param member      會員 User 實體
     * @param order       相關訂單
     * @param pointsToUse 要使用的點數
     * @throws BadRequestException 如果點數不足
     */
    @Transactional(propagation = Propagation.MANDATORY) // 確保此方法在現有交易中執行
    public void usePoints(User member, Order order, Long pointsToUse) {
        if (member == null || pointsToUse == null || pointsToUse <= 0) {
            return; // 沒有會員或沒使用點數，直接返回
        }

        MemberProfile profile = memberProfileRepository.findById(member.getUserId())
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
        MemberPointLog log = new MemberPointLog(member, order, -pointsToUse, newTotalPoints, reason);
        memberPointLogRepository.save(log);
    }

    /**
     * 賺取點數 (在訂單完成時呼叫)
     * 
     * @param member       會員 User 實體
     * @param order        相關訂單
     * @param pointsEarned 賺取的點數
     */
    @Transactional(propagation = Propagation.MANDATORY) // 確保此方法在現有交易中執行
    public void earnPoints(User member, Order order, Long pointsEarned) {
        if (member == null || pointsEarned == null || pointsEarned <= 0) {
            return; // 沒有會員或沒賺取點數，直接返回
        }

        MemberProfile profile = memberProfileRepository.findById(member.getUserId())
                .orElseThrow(() -> new BadRequestException("使用者 ID " + member.getUserId() + " 不是會員"));
        if (profile == null) {
            // 理論上此時 profile 不應為 null，但還是加上防禦性檢查
            System.err.println("警告：試圖為非會員 User ID " + member.getUserId() + " 增加點數");
            return;
        }

        // 增加點數
        long newTotalPoints = profile.getTotalPoints() + pointsEarned;
        profile.setTotalPoints(newTotalPoints);
        memberProfileRepository.save(profile); // 儲存更新後的 Profile

        // 記錄 Log
        String reason = "訂單消費獲得 (單號: " + (order != null ? order.getOrderNumber() : "N/A") + ")";
        MemberPointLog log = new MemberPointLog(member, order, pointsEarned, newTotalPoints, reason);
        memberPointLogRepository.save(log);
    }

}
