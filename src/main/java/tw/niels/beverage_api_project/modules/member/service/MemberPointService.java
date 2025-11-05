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

    // TODO: 定義點數規則，例如 1 元積 1 點，10 點折 1 元
    private static final BigDecimal POINTS_PER_DOLLAR = BigDecimal.ONE; // 每 1 元累積 1 點
    private static final BigDecimal DOLLARS_PER_10_POINTS = BigDecimal.ONE; // 每 10 點折抵 1 元

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
    public BigDecimal calculateDiscountAmount(Long pointsToUse) {
        if (pointsToUse == null || pointsToUse <= 0) {
            return BigDecimal.ZERO;
        }
        // 計算可折抵金額 (例如：每 10 點折 1 元)
        return DOLLARS_PER_10_POINTS.multiply(new BigDecimal(pointsToUse / 10)); // 整數除法取商
    }

    /**
     * 計算訂單完成後應獲得的點數
     * 
     * @param finalAmount 訂單最終金額
     * @return 應獲得的點數
     */
    public Long calculatePointsEarned(BigDecimal finalAmount) {
        if (finalAmount == null || finalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0L;
        }
        // 計算應獲得點數 (例如：每 1 元積 1 點，無條件捨去)
        return finalAmount.multiply(POINTS_PER_DOLLAR).longValue();
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

        MemberProfile profile = member.getMemberProfile();
        if (profile == null) {
            System.err.println("警告：試圖為非會員 User ID " + member.getUserId() + " 退還點數");
            return;
        }

        // 增加點數 (退還)
        long newTotalPoints = profile.getTotalPoints() + pointsToRefund;
        profile.setTotalPoints(newTotalPoints);
        memberProfileRepository.save(profile); // 儲存更新後的 Profile

        // 記錄 Log
        String reason = "訂單取消點數返還 (單號: " + (order != null ? order.getOrderNumber() : "N/A") + ")";
        MemberPointLog log = new MemberPointLog(member, order, pointsToRefund, newTotalPoints, reason);
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

        MemberProfile profile = member.getMemberProfile();
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

        MemberProfile profile = member.getMemberProfile();
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
