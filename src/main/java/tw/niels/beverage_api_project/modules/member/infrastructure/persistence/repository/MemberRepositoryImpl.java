package tw.niels.beverage_api_project.modules.member.infrastructure.persistence.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.member.domain.model.Member;
import tw.niels.beverage_api_project.modules.member.domain.model.PointTransaction;
import tw.niels.beverage_api_project.modules.member.domain.repository.MemberRepository;
import tw.niels.beverage_api_project.modules.member.infrastructure.mapper.MemberMapper;
import tw.niels.beverage_api_project.modules.member.repository.MemberPointLogJpaRepository;
import tw.niels.beverage_api_project.modules.user.entity.UserEntity;
import tw.niels.beverage_api_project.modules.user.entity.profile.MemberProfileEntity;
import tw.niels.beverage_api_project.modules.user.enums.Gender;
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

    // 用來以 getReference() 取得品牌關聯代理（取代 new BrandEntity() + setId()，避免 flush 時多餘的存在性查詢）
    @PersistenceContext
    private EntityManager entityManager;

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
     * 建立新會員（含 User 帳號與 MemberProfile）。
     *
     * 處理流程：
     * 1. 建立 UserEntity（品牌關聯使用 Proxy 物件，避免額外查詢）
     * 2. 建立 MemberProfileEntity，從 Member Domain Model 映射個人資料
     * 3. 設定雙向關聯，透過 CascadeType.ALL 一次儲存
     * 4. 回傳資料庫生成的新 User ID（即會員 ID）
     *
     * @param member       已初始化的 Member Domain Model（id 為 null）
     * @param passwordHash 已雜湊的密碼（由 Application Layer 處理）
     * @return 資料庫生成的新會員 ID
     */
    @Override
    @Transactional
    public Long create(Member member, String passwordHash) {
        // ─── 步驟 1：建立 UserEntity ──────────────────────────────────────
        var jpaUser = new UserEntity();

        // 品牌關聯：改用 EntityManager.getReference()（P3 清理），僅帶入 ID 且不觸發額外查詢；
        // 原本的 new BrandEntity() + setId() 是「未管理的暫態物件」，Hibernate flush 時會再查一次確認它是否存在。
        jpaUser.setBrand(entityManager.getReference(BrandEntity.class, member.getBrandId()));

        jpaUser.setPrimaryPhone(member.getProfile().phone());
        jpaUser.setPasswordHash(passwordHash);
        jpaUser.setIsActive(member.isActive());

        // ─── 步驟 2：建立 MemberProfileEntity ────────────────────────────
        var jpaProfile = new MemberProfileEntity();
        jpaProfile.setFullName(member.getProfile().fullName());
        jpaProfile.setEmail(member.getProfile().email());

        // LocalDate → java.sql.Date
        if (member.getProfile().birthday() != null) {
            jpaProfile.setBirthDate(java.sql.Date.valueOf(member.getProfile().birthday()));
        }

        // String → Gender enum（MemberProfile VO 以 String 存儲，MemberProfileEntity 以 enum 存儲）
        if (member.getProfile().gender() != null) {
            try {
                jpaProfile.setGender(Gender.valueOf(member.getProfile().gender()));
            } catch (IllegalArgumentException ignored) {
                // 無效的性別值直接略過，保持 null
            }
        }

        jpaProfile.setTotalPoints(member.getPoints() != null ? member.getPoints().value() : 0L);

        // ─── 步驟 3：設定雙向關聯並儲存（CascadeType.ALL 自動儲存 MemberProfile）
        jpaUser.setMemberProfile(jpaProfile);
        var saved = userJpaRepo.save(jpaUser);

        return saved.getId();
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
        // 修正(P0-6)：UserJpaRepository 已覆寫 findById() 並直接拋出 UnsupportedOperationException，
        // 改用帶 brandId 的 findByBrand_IdAndId()，同時維持多租戶隔離。
        if (member.getId() == null) {
            throw new IllegalArgumentException("儲存會員時 id 不可為 null（新增請改用 create()）");
        }
        // 修正(多租戶隔離)：原本找不到該品牌下的 User 時只是略過，後續仍會「不分品牌」地用 id 去載入並更新
        // MemberProfile，等於可以改到別的品牌的會員積點。找不到就直接拋 404，整筆交易回滾。
        UserEntity jpaUser = userJpaRepo.findByBrand_IdAndId(member.getBrandId(), member.getId())
            .orElseThrow(() -> new ResourceNotFoundException("找不到會員，ID：" + member.getId()));
        // jpaUser 為受管實體，異動由 dirty checking 在 commit 時寫入，不需要再呼叫 save()
        jpaUser.setIsActive(member.isActive());

        // ─── 步驟 2：更新 MemberProfile（totalPoints、個人資料）─────────────
        // 併發保護：MemberProfileEntity 有 @Version（樂觀鎖），同時修改積點時後提交者會拋
        // ObjectOptimisticLockingFailureException（→ HTTP 409，或由事件監聽器重試），不會靜默覆寫。
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

        // ─── 步驟 3：儲存新增的積點交易紀錄 ─────────────────────────────
        // pullPointTransactions() 取出並清空 domain 中的待處理交易
        var newTransactions = member.pullPointTransactions();
        if (!newTransactions.isEmpty()) {
            // 最後一筆交易後的餘額 = 當前積點餘額
            long finalBalance = member.getPoints() != null ? member.getPoints().value() : 0L;

            // 修正(P0-6)：逐筆累計 balance_after，而不是全部沿用最終餘額。
            // 起始餘額 = 最終餘額 - 這批交易的異動總和；再依發生順序（由前往後）累加，
            // 每筆紀錄的 balance_after 即為「該筆交易完成後」的正確餘額。
            long totalChange = newTransactions.stream().mapToLong(PointTransaction::getAmount).sum();
            long runningBalance = finalBalance - totalChange;

            for (PointTransaction tx : newTransactions) {
                runningBalance += tx.getAmount();
                // 會員關聯直接使用步驟 1 載入的受管 UserEntity，不再為每筆日誌 new 一個暫態 UserEntity
                tw.niels.beverage_api_project.modules.member.entity.MemberPointLogEntity logEntity = MemberMapper.toPointLogEntity(tx, runningBalance, jpaUser);
                if (logEntity != null) {
                    pointLogJpaRepo.save(logEntity);
                }
            }
        }
    }
}
