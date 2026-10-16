package tw.niels.beverage_api_project.modules.user.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import tw.niels.beverage_api_project.modules.user.entity.profile.MemberProfileEntity;

import java.util.Optional;

public interface MemberProfileJpaRepository extends JpaRepository<MemberProfileEntity, Long> {
    /**
     * 根據 userId 查找 MemberProfile。
     *
     * 修正(B-2 註解)：先前註解宣稱使用 PESSIMISTIC_WRITE，但此查詢實際上【沒有】加任何鎖。
     * 併發保護來自 MemberProfileEntity 的 @Version 樂觀鎖：
     * 兩筆交易同時修改積點時，後提交者會拋出 OptimisticLockingFailureException（→ HTTP 409），
     * 由呼叫端決定是否重試，不會發生靜默覆寫。
     */
    @Query("SELECT mp FROM MemberProfileEntity mp WHERE mp.userId = :userId")
    @NotNull
    Optional<MemberProfileEntity> findById(@NotNull Long userId);
}