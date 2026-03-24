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
}
