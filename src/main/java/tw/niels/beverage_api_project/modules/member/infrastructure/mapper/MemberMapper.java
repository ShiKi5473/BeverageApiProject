package tw.niels.beverage_api_project.modules.member.infrastructure.mapper;

import tw.niels.beverage_api_project.modules.member.domain.model.*;

import java.time.ZoneId;
import java.util.ArrayList;

/**
 * ============================================================
 * Bounded Context: Membership
 * Layer: Infrastructure - Mapper
 * ============================================================
 *
 * Member BC - JPA Entity ↔ Domain Model 雙向映射工具
 *
 * 映射規則：
 * - Member 資料來源：User（id、brandId、isActive、phone）
 *   + MemberProfile（fullName、email、birthday、gender、totalPoints）
 * - MemberLevel：JPA 無對應欄位，預設 REGULAR
 *   （TODO：待 member_profiles 表加入 level 欄位後更新）
 * - MemberPointLog ↔ PointTransaction：
 *   - JPA 無 type 欄位，依 pointsChange 正負推斷 PointTransactionType
 *   - pointsChange > 0 → EARN_ORDER，< 0 → REDEEM_ORDER，= 0 → ADMIN_ADJUST
 * ============================================================
 */
public class MemberMapper {

    /**
     * User + MemberProfile JPA Entities → Domain Member Aggregate
     *
     * @param jpaUser    users 表的 JPA Entity（必填）
     * @param jpaProfile member_profiles 表的 JPA Entity（可為 null）
     * @return Domain Member，若 jpaUser 為 null 回傳 null
     */
    public static Member toDomain(
            tw.niels.beverage_api_project.modules.user.entity.UserEntity jpaUser,
            tw.niels.beverage_api_project.modules.user.entity.profile.MemberProfileEntity jpaProfile) {
        if (jpaUser == null) return null;

        // MemberProfile VO 映射
        MemberProfile domainProfile;
        if (jpaProfile != null) {
            domainProfile = new MemberProfile(
                jpaProfile.getFullName() != null ? jpaProfile.getFullName()
                    : (jpaUser.getPrimaryPhone() != null ? jpaUser.getPrimaryPhone() : "未知"),
                jpaUser.getPrimaryPhone(),     // phone 在 users 表
                jpaProfile.getEmail(),
                // Date/Timestamp → LocalDate
                jpaProfile.getBirthDate() != null
                    ? jpaProfile.getBirthDate().toInstant()
                          .atZone(ZoneId.systemDefault()).toLocalDate()
                    : null,
                jpaProfile.getGender() != null ? jpaProfile.getGender().name() : null
            );
        } else {
            // 無 MemberProfile 時，以電話號碼作為 fullName 的 fallback
            domainProfile = new MemberProfile(
                jpaUser.getPrimaryPhone() != null ? jpaUser.getPrimaryPhone() : "未知",
                jpaUser.getPrimaryPhone(),
                null, null, null
            );
        }

        // MemberPoint VO 映射（totalPoints 在 member_profiles 表）
        MemberPoint domainPoints = (jpaProfile != null && jpaProfile.getTotalPoints() != null)
            ? MemberPoint.of(jpaProfile.getTotalPoints())
            : MemberPoint.zero();

        return new Member(
            jpaUser.getId(),
            jpaUser.getBrand() != null ? jpaUser.getBrand().getId() : null,
            domainProfile,
            domainPoints,
            MemberLevel.regular(),
            Boolean.TRUE.equals(jpaUser.getIsActive())
        );
    }

    /**
     * Domain PointTransaction → JPA MemberPointLog Entity
     *
     * @param domain      Domain PointTransaction
     * @param balanceAfter 異動後的餘額（由 RepositoryImpl 在 save() 時傳入）
     * @return JPA MemberPointLog Entity
     */
    public static tw.niels.beverage_api_project.modules.member.entity.MemberPointLogEntity toPointLogEntity(
            PointTransaction domain, Long balanceAfter) {
        if (domain == null) return null;

        var log = new tw.niels.beverage_api_project.modules.member.entity.MemberPointLogEntity();

        // Member（User）Proxy
        if (domain.getMemberId() != null) {
            var user = new tw.niels.beverage_api_project.modules.user.entity.UserEntity();
            user.setId(domain.getMemberId());
            log.setMember(user);
        }

        // Order Proxy（可為 null）- 註：PointTransaction Domain 目前無 orderId 欄位，暫不進行映射
        // 若未來 domain 加入此欄位，需取消註解下方
        /*
        if (domain.getOrderId() != null) {
            var order = new tw.niels.beverage_api_project.modules.order.entity.OrderEntity();
            order.setId(domain.getOrderId());
            log.setOrder(order);
        }
        */

        log.setPointsChange(domain.getAmount());
        log.setBalanceAfter(balanceAfter);
        log.setReason(domain.getReason());
        // ⚠️ JPA MemberPointLog 無 type 欄位，reason 字串作為替代
        return log;
    }

    /**
     * JPA MemberPointLog → Domain PointTransaction
     *
     * PointTransactionType 依 pointsChange 推斷：
     * - > 0 → EARN_ORDER（累積積點）
     * - < 0 → REDEEM_ORDER（使用積點）
     * - = 0 → ADMIN_ADJUST（管理員調整）
     */
    public static PointTransaction toPointTransactionDomain(
            tw.niels.beverage_api_project.modules.member.entity.MemberPointLogEntity jpa) {
        if (jpa == null) return null;

        // 依 pointsChange 推斷 type（JPA 無對應欄位）
        PointTransactionType type;
        if (jpa.getPointsChange() == null || jpa.getPointsChange() == 0) {
            type = PointTransactionType.ADMIN_ADJUST;
        } else if (jpa.getPointsChange() > 0) {
            type = PointTransactionType.EARN_ORDER;
        } else {
            type = PointTransactionType.REDEEM_ORDER;
        }

        var domain = PointTransaction.of(
            jpa.getMember() != null ? jpa.getMember().getId() : null,
            jpa.getPointsChange(),
            type,
            jpa.getReason()
        );
        
        if (jpa.getId() != null) {
            try {
                var setIdMethod = PointTransaction.class.getDeclaredMethod("setId", Long.class);
                setIdMethod.setAccessible(true);
                setIdMethod.invoke(domain, jpa.getId());
            } catch (Exception e) {}
        }
        return domain;
    }
}
