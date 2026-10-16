package tw.niels.beverage_api_project.modules.member.domain.repository;

import tw.niels.beverage_api_project.modules.member.domain.model.Member;

import java.util.Optional;

/**
 * ============================================================
 * Bounded Context: Membership & Rewards
 * Layer: Domain - Repository Interface
 * ============================================================
 *
 * 會員倉儲介面
 *
 * 定義對會員資料的存取合約。
 * ============================================================
 */
public interface MemberRepository {

    Optional<Member> findById(Long brandId, Long memberId);
    Optional<Member> findByPhone(Long brandId, String phone);
    void save(Member member);

    /**
     * 建立新會員（含對應的 User 帳號）。
     *
     * 由 Application Layer 傳入已雜湊的密碼，
     * Infrastructure Layer 負責建立 UserEntity + MemberProfileEntity 並儲存。
     *
     * @param member       已完成初始化的 Member Domain Model
     * @param passwordHash 已雜湊的密碼（由 Application Layer 使用 PasswordEncoder 處理）
     * @return 資料庫生成的新會員 ID
     */
    Long create(Member member, String passwordHash);
}
