package tw.niels.beverage_api_project.modules.member.infrastructure.mapper;

import tw.niels.beverage_api_project.modules.member.domain.model.*;

import java.time.Instant;
import java.time.ZoneId;

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
 * - PointTransaction → MemberPointLog（單向，積點紀錄為 append-only 稽核日誌）：
 *   - JPA 無 type 欄位，交易類型以 reason 字串留存
 *   - 會員關聯由呼叫端傳入受管實體／getReference 代理
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
                // 修正：java.sql.Date 的 toInstant() 會直接拋 UnsupportedOperationException，
                // 改用 Instant.ofEpochMilli(getTime())，不論 Hibernate 回傳 java.util.Date / Timestamp / java.sql.Date 都安全。
                // 時區刻意沿用 JVM 預設：寫入端 java.sql.Date.valueOf(LocalDate) 也是以 JVM 預設時區換算，兩端才會對稱
                // （Docker 映像已設 TZ=Asia/Taipei）。
                jpaProfile.getBirthDate() != null
                    ? Instant.ofEpochMilli(jpaProfile.getBirthDate().getTime())
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
     * @param memberRef   該會員的 UserEntity（受管實體或 getReference 取得的代理）
     * @return JPA MemberPointLog Entity
     */
    public static tw.niels.beverage_api_project.modules.member.entity.MemberPointLogEntity toPointLogEntity(
            PointTransaction domain, Long balanceAfter,
            tw.niels.beverage_api_project.modules.user.entity.UserEntity memberRef) {
        if (domain == null) return null;

        var log = new tw.niels.beverage_api_project.modules.member.entity.MemberPointLogEntity();

        // 修正(P3)：會員關聯改由呼叫端傳入受管實體／getReference 代理，
        // 不再 new UserEntity() + setId()（那會讓 Hibernate flush 時多一次「是否存在」查詢，每筆積點日誌都要查一次）。
        log.setMember(memberRef);

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

    // 已移除(P3 清理)：原 toPointTransactionDomain(MemberPointLogEntity)。
    // 該方法沒有任何呼叫端，且用「反射 + setAccessible」呼叫 PointTransaction 的 package-private setId、
    // 失敗時還吞掉例外（catch (Exception e) {}）。積點紀錄是 append-only 的稽核日誌，不需要從 JPA 還原回 Domain。
}
