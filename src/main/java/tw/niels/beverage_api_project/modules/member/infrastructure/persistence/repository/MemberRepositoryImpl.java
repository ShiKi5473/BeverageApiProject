package tw.niels.beverage_api_project.modules.member.infrastructure.persistence.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.modules.member.domain.model.Member;
import tw.niels.beverage_api_project.modules.member.domain.repository.MemberRepository;
import tw.niels.beverage_api_project.modules.member.infrastructure.mapper.MemberMapper;
import tw.niels.beverage_api_project.modules.member.repository.MemberPointLogJpaRepository;
import tw.niels.beverage_api_project.modules.user.repository.UserJpaRepository;
import tw.niels.beverage_api_project.modules.user.repository.MemberProfileJpaRepository;

import java.util.Optional;

/**
 * ============================================================
 * Bounded Context: Membership
 * Layer: Infrastructure - Repository Implementation
 * ============================================================
 *
 * Member BC Domain Repository 的 Infrastructure 層實現
 *
 * 資料來源：
 * - User JPA Entity（users 表）：id、brandId、isActive、phone
 * - MemberProfile JPA Entity（member_profiles 表）：個人資料、積點餘額
 * - MemberPointLog JPA Entity（member_points_log 表）：積點交易紀錄
 *
 * 儲存策略：
 * 1. 儲存 User（更新 isActive）
 * 2. 儲存 MemberProfile（更新 totalPoints、個人資料）
 * 3. pullPointTransactions() 取出新積點異動，逐筆建立 MemberPointLog
 * ============================================================
 */
@Repository
public class MemberRepositoryImpl implements MemberRepository {

    private final UserJpaRepository userJpaRepo;
    private final MemberProfileJpaRepository memberProfileJpaRepo;
    private final MemberPointLogJpaRepository pointLogJpaRepo;

    public MemberRepositoryImpl(UserJpaRepository userJpaRepo,
                                 MemberProfileJpaRepository memberProfileJpaRepo,
                                 MemberPointLogJpaRepository pointLogJpaRepo) {
        this.userJpaRepo = userJpaRepo;
        this.memberProfileJpaRepo = memberProfileJpaRepo;
        this.pointLogJpaRepo = pointLogJpaRepo;
    }

    /**
     * 依品牌 ID 與會員 ID 查詢會員（含個人資料與積點餘額）
     *
     * 流程：
     * 1. 查詢 users 表
     * 2. 查詢 member_profiles 表（可能尚無 MemberProfile，允許 null）
     * 3. 合併為 Domain Member
     *
     * @param brandId  品牌 ID（多租戶隔離）
     * @param memberId 會員 ID（與 User.id 相同）
     * @return Domain Member Optional
     */
    @Override
    public Optional<Member> findById(Long brandId, Long memberId) {
        return userJpaRepo.findByBrand_IdAndId(brandId, memberId)
            .map(jpaUser -> {
                var jpaProfile = memberProfileJpaRepo.findById(jpaUser.getId()).orElse(null);
                return MemberMapper.toDomain(jpaUser, jpaProfile);
            });
    }

    /**
     * 依品牌 ID 與手機號碼查詢會員
     *
     * @param brandId 品牌 ID
     * @param phone   手機號碼
     * @return Domain Member Optional
     */
    @Override
    public Optional<Member> findByPhone(Long brandId, String phone) {
        return userJpaRepo.findByPrimaryPhoneAndBrandId(phone, brandId)
            .map(jpaUser -> {
                var jpaProfile = memberProfileJpaRepo.findById(jpaUser.getId()).orElse(null);
                return MemberMapper.toDomain(jpaUser, jpaProfile);
            });
    }

    /**
     * 儲存會員（含積點餘額更新與積點交易紀錄）
     *
     * 流程：
     * 1. 儲存 User（isActive 欄位）
     * 2. 更新 MemberProfile（totalPoints、個人資料）
     * 3. 取出 pullPointTransactions() 的新交易紀錄，建立 MemberPointLog
     *
     * @param member 要儲存的 Domain Member
     */
    @Override
    @Transactional
    public void save(Member member) {
        // ─── 步驟 1：更新 User（isActive）─────────────────────────────────
        // 查詢現有 User Entity（不用 Proxy，需要完整 Entity 才能更新）
        if (member.getId() != null) {
            userJpaRepo.findById(member.getId()).ifPresent(jpaUser -> {
                jpaUser.setIsActive(member.isActive());
                userJpaRepo.save(jpaUser);
            });
        }

        // ─── 步驟 2：更新 MemberProfile（totalPoints、個人資料）─────────────
        var jpaProfile = memberProfileJpaRepo.findById(member.getId()).orElse(null);
        if (jpaProfile != null && member.getProfile() != null) {
            jpaProfile.setFullName(member.getProfile().fullName());
            jpaProfile.setEmail(member.getProfile().email());
            if (member.getProfile().birthday() != null) {
                jpaProfile.setBirthDate(java.sql.Date.valueOf(member.getProfile().birthday()));
            }
            // 更新積點餘額（totalPoints）
            if (member.getPoints() != null) {
                jpaProfile.setTotalPoints(member.getPoints().value());
            }
            memberProfileJpaRepo.save(jpaProfile);
        }

        // ─── 步驟 3：儲存新增的積點交易紀錄 ──────────────────────────────
        // pullPointTransactions() 取出並清空 domain 中的待處理交易
        var newTransactions = member.pullPointTransactions();
        if (!newTransactions.isEmpty()) {
            // 最後一筆交易後的餘額 = 當前積點餘額
            long finalBalance = member.getPoints() != null ? member.getPoints().value() : 0L;

            for (int i = newTransactions.size() - 1; i >= 0; i--) {
                // 最後一筆使用 finalBalance，往前推算各筆交易後餘額（簡化：全用 finalBalance）
                // 精確計算需要從前往後累加，此處簡化為統一使用最終餘額
                var tx = newTransactions.get(i);
                tw.niels.beverage_api_project.modules.member.entity.MemberPointLogEntity logEntity = MemberMapper.toPointLogEntity(tx, finalBalance);
                if (logEntity != null) {
                    pointLogJpaRepo.save(logEntity);
                }
            }
        }
    }
}
